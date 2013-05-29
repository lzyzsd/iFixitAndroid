package com.dozuki.ifixit.ui.guide.view;

import android.content.Intent;
import android.os.Bundle;
import android.speech.SpeechRecognizer;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.dozuki.ifixit.MainApplication;
import com.dozuki.ifixit.R;
import com.dozuki.ifixit.model.guide.Guide;
import com.dozuki.ifixit.ui.IfixitActivity;
import com.dozuki.ifixit.ui.guide.create.StepEditActivity;
import com.dozuki.ifixit.ui.topic_view.TopicGuideListFragment;
import com.dozuki.ifixit.util.APIEvent;
import com.dozuki.ifixit.util.APIService;
import com.dozuki.ifixit.util.SpeechCommander;
import com.marczych.androidimagemanager.ImageManager;
import com.squareup.otto.Subscribe;
import com.viewpagerindicator.CirclePageIndicator;
import org.holoeverywhere.widget.ProgressBar;

import java.util.List;

public class GuideViewActivity extends IfixitActivity implements OnPageChangeListener {
   private static final int MAX_LOADING_IMAGES = 9;
   private static final int MAX_STORED_IMAGES = 9;
   private static final int MAX_WRITING_IMAGES = 10;
   private static final String NEXT_COMMAND = "next";
   private static final String PREVIOUS_COMMAND = "previous";
   private static final String HOME_COMMAND = "home";
   private static final String PACKAGE_NAME = "com.dozuki.ifixit";
   public static final String CURRENT_PAGE = "CURRENT_PAGE";
   public static final String SAVED_GUIDE = "SAVED_GUIDE";
   public static final String SAVED_GUIDEID = "SAVED_GUIDEID";
   public static final String TOPIC_NAME_KEY = "TOPIC_NAME_KEY";
   public static final String FROM_EDIT = "FROM_EDIT_KEY";
   public static final String INBOUND_STEP_ID = "INBOUND_STEP_ID";

   public static final int MENU_EDIT_GUIDE = 2;

   private GuideViewAdapter mGuideAdapter;
   private int mGuideid;
   private Guide mGuide;
   private SpeechCommander mSpeechCommander;
   private int mCurrentPage = -1;
   private ImageManager mImageManager;
   private ViewPager mPager;
   private CirclePageIndicator mIndicator;
   private ProgressBar mProgressBar;
   private ImageView mNextPageImage;
   private boolean mFromEdit = false;
   private int mInboundStepId = -1;

   /////////////////////////////////////////////////////
   // LIFECYCLE
   /////////////////////////////////////////////////////

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      setContentView(R.layout.guide_main);

      mImageManager = MainApplication.get().getImageManager();
      mImageManager.setMaxLoadingImages(MAX_LOADING_IMAGES);
      mImageManager.setMaxStoredImages(MAX_STORED_IMAGES);
      mImageManager.setMaxWritingImages(MAX_WRITING_IMAGES);

      mPager = (ViewPager) findViewById(R.id.guide_pager);
      mIndicator = (CirclePageIndicator) findViewById(R.id.indicator);
      mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
      mNextPageImage = (ImageView) findViewById(R.id.next_page_image);

      if (savedInstanceState != null) {
         mGuideid = savedInstanceState.getInt(SAVED_GUIDEID);
         mGuide = (Guide) savedInstanceState.getSerializable(SAVED_GUIDE);

         if (mGuide != null) {
            mCurrentPage = savedInstanceState.getInt(CURRENT_PAGE);

            setGuide(mGuide, mCurrentPage);
            mIndicator.setCurrentItem(mCurrentPage);
            mPager.setCurrentItem(mCurrentPage);
         } else {
            getGuide(mGuideid);
         }
      } else {
         Intent intent = getIntent();

         mGuideid = -1;

         // Handle when the activity is started from an external app.  (like a link)
         if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            List<String> segments = intent.getData().getPathSegments();

            try {
               mGuideid = Integer.parseInt(segments.get(2));
            } catch (Exception e) {
               displayError();
               Log.e("iFixit", "Problem parsing guide");
            }
         } else {
            Bundle extras = intent.getExtras();

            if (extras != null) {
               if (extras.containsKey(FROM_EDIT)) {
                  mFromEdit = extras.getBoolean(FROM_EDIT);
               }
               // We're coming from the Topics GuideList
               if (extras.containsKey(TopicGuideListFragment.GUIDEID)) {
                  mGuideid = extras.getInt(TopicGuideListFragment.GUIDEID);

               // We're coming from StepEdit
               } else if (extras.containsKey(GuideViewActivity.SAVED_GUIDEID)) {
                  mGuideid = extras.getInt(GuideViewActivity.SAVED_GUIDEID);
               }

               if (extras.containsKey(GuideViewActivity.SAVED_GUIDE)) {
                  mGuide = (Guide) extras.getSerializable(GuideViewActivity.SAVED_GUIDE);
               }

               mInboundStepId = extras.getInt(INBOUND_STEP_ID);
               mCurrentPage = extras.getInt(GuideViewActivity.CURRENT_PAGE, 0);
            }
         }
         if (mGuide == null) {
            getGuide(mGuideid);
         } else {
            setGuide(mGuide, mCurrentPage);
         }
      }

      mNextPageImage.setOnTouchListener(new View.OnTouchListener() {
         public boolean onTouch(View v, MotionEvent event) {
            if (mCurrentPage == 0) {
               nextStep();

               return true;
            }

            return false;
         }
      });

      if (!mFromEdit) {
         getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      }

      //initSpeechRecognizer();
   }

   @Override
   public void onSaveInstanceState(Bundle state) {
      /**
       * TODO Figure out why we don't super.onSaveInstanceState(). I think
       * this causes step fragments to not maintain state across orientation
       * changes (selected thumbnail). However, I remember this failing with a
       * call to super.onSavInstanceState(). Investigate.
       */
      state.putSerializable(SAVED_GUIDEID, mGuideid);
      state.putSerializable(SAVED_GUIDE, mGuide);
      state.putInt(CURRENT_PAGE, mCurrentPage);
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      menu.add(1, MENU_EDIT_GUIDE, 0, R.string.edit_guide)
       .setIcon(R.drawable.ic_action_edit)
       .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
      return super.onCreateOptionsMenu(menu);
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      Intent intent;
      switch (item.getItemId()) {
         case MENU_EDIT_GUIDE:
            intent = new Intent(this, StepEditActivity.class);
            int stepNum = 0;

            // Take into account the introduction page.
            if (mCurrentPage != 0) {
               stepNum = mCurrentPage - 1;
            }
            int stepGuideid = mGuide.getStep(stepNum).getGuideid();
            // If the step is part of a prerequisite guide, store the parents guideid so that we can get back from
            // editing this prerequisite.
            if (stepGuideid != mGuide.getGuideid()) {
               intent.putExtra(StepEditActivity.PARENT_GUIDE_ID_KEY, mGuide.getGuideid());
            }
            // We have to pass along the steps guideid to account for prerequisite guides.
            intent.putExtra(StepEditActivity.GUIDE_ID_KEY, stepGuideid);
            intent.putExtra(StepEditActivity.GUIDE_STEP_ID, mGuide.getStep(stepNum).getStepid());
            startActivity(intent);
            break;
         default:
            return (super.onOptionsItemSelected(item));
      }
      return true;
   }

   /////////////////////////////////////////////////////
   // NOTIFICATION LISTENERS
   /////////////////////////////////////////////////////

   @Subscribe
   public void onGuide(APIEvent.ViewGuide event) {
      if (!event.hasError()) {
         if (mGuide == null) {
            Guide guide = event.getResult();
            if (mInboundStepId != -1) {
               for (int i = 0; i < guide.getSteps().size(); i++) {
                  if (mInboundStepId == guide.getStep(i).getStepid()) {
                     mCurrentPage = i + 1;
                  }
               }
            }
            setGuide(event.getResult(), mCurrentPage); // Account for the introduction page
         }
      } else {
         APIService.getErrorDialog(GuideViewActivity.this, event.getError(),
          APIService.getGuideAPICall(mGuideid)).show();
      }
   }

   /////////////////////////////////////////////////////
   // HELPERS
   /////////////////////////////////////////////////////

   private void setGuide(Guide guide, int currentPage) {
      if (guide == null) {
         displayError();
         Log.e("GuideViewActivity", "Guide is not set");
         return;
      }

      mProgressBar.setVisibility(View.GONE);
      mGuide = guide;

      getSupportActionBar().setTitle(mGuide.getTitle());

      mGuideAdapter = new GuideViewAdapter(this.getSupportFragmentManager(), mGuide);

      mPager.setAdapter(mGuideAdapter);
      mIndicator.setOnPageChangeListener(this);
      mIndicator.setViewPager(mPager);

      final float density = getResources().getDisplayMetrics().density;
      mIndicator.setBackgroundColor(0x00FFFFFF);
      mIndicator.setRadius(6 * density);
      mIndicator.setPageColor(0xFFFFFFFF);
      mIndicator.setFillColor(0xFF444444);
      mIndicator.setStrokeColor(0xFF000000);
      mIndicator.setStrokeWidth((int) (1.5 * density));

      mPager.setVisibility(View.VISIBLE);

      mPager.setCurrentItem(currentPage);
      mIndicator.setCurrentItem(currentPage);

      onPageSelected(currentPage);
   }

   @Override
   public void onDestroy() {
      super.onDestroy();

      if (mSpeechCommander != null) {
         mSpeechCommander.destroy();
      }
   }

   @Override
   public void onPause() {
      super.onPause();

      if (mSpeechCommander != null) {
         mSpeechCommander.stopListening();
         mSpeechCommander.cancel();
      }
   }

   @Override
   public void onResume() {
      super.onResume();

      if (mSpeechCommander != null) {
         mSpeechCommander.startListening();
      }
   }

   public void getGuide(final int guideid) {
      mNextPageImage.setVisibility(View.GONE);

      APIService.call(this, APIService.getGuideAPICall(guideid));
   }

   private void displayError() {
      mProgressBar.setVisibility(View.GONE);
      // TODO Display error
   }

   private void nextStep() {
      mIndicator.setCurrentItem(mCurrentPage + 1);
   }

   private void previousStep() {
      mIndicator.setCurrentItem(mCurrentPage - 1);
   }

   private void guideHome() {
      mIndicator.setCurrentItem(0);
   }

   protected int getIndicatorHeight() {
      int indicatorHeight = mIndicator.getHeight();

      // Unbelievably horrible hack that fixes a problem when
      // getIndicatorHeight() returns 0 after a orientation change, causing the
      // Main image view to calculate to large and the thumbnails are hidden by
      // the CircleIndicator.
      // TODO: Figure out why this is actually happening and the right way to do
      //       this.
      if (indicatorHeight == 0) {
         indicatorHeight = 49;
      }

      return indicatorHeight;
   }

   @SuppressWarnings("unused")
   private void initSpeechRecognizer() {
      if (!SpeechRecognizer.isRecognitionAvailable(getBaseContext())) {
         return;
      }

      mSpeechCommander = new SpeechCommander(this, PACKAGE_NAME);

      mSpeechCommander.addCommand(NEXT_COMMAND, new SpeechCommander.Command() {
         public void performCommand() {
            nextStep();
         }
      });

      mSpeechCommander.addCommand(PREVIOUS_COMMAND,
       new SpeechCommander.Command() {
          public void performCommand() {
             previousStep();
          }
       });

      mSpeechCommander.addCommand(HOME_COMMAND, new SpeechCommander.Command() {
         public void performCommand() {
            guideHome();
         }
      });

      mSpeechCommander.startListening();
   }

   @Override
   public void onPageScrollStateChanged(int arg0) {}

   @Override
   public void onPageScrolled(int arg0, float arg1, int arg2) {}

   @Override
   public void onPageSelected(int page) {
      if (mCurrentPage == page) {
         return;
      }

      mCurrentPage = page;
      final int visibility;
      Animation anim;

      if (mCurrentPage == 0) {
         anim = new AlphaAnimation(0.00f, 1.00f);
         visibility = View.VISIBLE;
      } else if (mCurrentPage == 1) {
         anim = new AlphaAnimation(1.00f, 0.00f);
         visibility = View.GONE;
      } else {
         mNextPageImage.setVisibility(View.GONE);
         return;
      }

      if (anim != null && mNextPageImage.getVisibility() != visibility) {
         anim.setDuration(400);
         anim.setAnimationListener(new AnimationListener() {
            public void onAnimationStart(Animation animation) {}

            public void onAnimationRepeat(Animation animation) {}

            public void onAnimationEnd(Animation animation) {
               mNextPageImage.setVisibility(visibility);
            }
         });

         mNextPageImage.startAnimation(anim);
      }
   }

}
