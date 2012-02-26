package com.ifixit.android.ifixit;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class GuideStepViewFragment extends Fragment {
   protected static final String IMAGEID = "imageid";

   private TextView mTitle;
   private ThumbnailView mThumbs;
   private LoaderImage mMainImage;
   private GuideStep mStep;
   private ImageManager mImageManager;
   private StepTextArrayAdapter mTextAdapter;
   private ListView mLineList;
   private Typeface mFont;

   public GuideStepViewFragment() {

   }
   
   public GuideStepViewFragment(ImageManager im, GuideStep step) {   
      mStep = step;
      mImageManager = im;
   }
   
   @Override
   public void onCreate(Bundle savedState) {
	   super.onCreate(savedState);

      if (mImageManager == null) {
         mImageManager = ((MainApplication)getActivity().getApplication()).
          getImageManager();
      }
   }

   @Override
   public View onCreateView(LayoutInflater inflater, ViewGroup container,
	 Bundle savedInstanceState) {
	   
	   View view = inflater.inflate(R.layout.guide_step, container, false);
      mFont = Typeface.createFromAsset(getActivity().getAssets(),
       "fonts/Ubuntu-B.ttf");  
	      
      mLineList = (ListView)view.findViewById(R.id.step_text_list);
      mTitle = (TextView)view.findViewById(R.id.step_title);
      mTitle.setTypeface(mFont);
      
      mMainImage = (LoaderImage)view.findViewById(R.id.main_image);

      mThumbs = (ThumbnailView)view.findViewById(R.id.thumbnails);
      mThumbs.setMainImage(mMainImage);
	
      if (mStep != null)
         setStep();
      
      return view;
                  
      /*mMainImage.setOnClickListener(new OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			// TODO Auto-generated method stub
			
		}
      });*/
   }
   
   public void setStep() {
      if (mStep.getTitle().length() == 0)
         mTitle.setText("Step " + mStep.getStepNum());
      else
         mTitle.setText(mStep.getTitle());

      mTextAdapter = new StepTextArrayAdapter(getActivity(),
       R.id.step_text_list, mStep.getLines());
      mLineList.setAdapter(mTextAdapter);
      
      mThumbs.setThumbs(mStep.mImages, mImageManager, getActivity());

      // Might be a problem if there are no images for a step...
      mImageManager.displayImage(mStep.mImages.get(0).getText() + ".large",
       getActivity(), mMainImage);         
   }
  
   public void setImageManager(ImageManager im) {
	  mImageManager = im;
   }
   
   public void setMainImage(String url) {
      mImageManager.displayImage(url, getActivity(), mMainImage);
   }

   public class StepTextArrayAdapter extends ArrayAdapter<StepLine> {
      private ArrayList<StepLine> mLines;
      private Context mContext;
      
      public StepTextArrayAdapter(Context context, int viewResourceId, 
       ArrayList<StepLine> lines) {
         super(context, viewResourceId, lines);
         
         mLines = lines;
         mContext = context;
      }
   
      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
         GuideStepLineView stepLine = (GuideStepLineView)convertView;

         if (stepLine == null) {
            stepLine = new GuideStepLineView(mContext);             
         } 

         stepLine.setLine(mLines.get(position));
         return stepLine;
      }
   }
}