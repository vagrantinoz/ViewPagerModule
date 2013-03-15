/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package com.oxgcp.viewPager;

import java.util.ArrayList;
import java.lang.Math;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiEventHelper;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;
import org.appcelerator.titanium.view.TiUIView;

import com.oxgcp.viewPager.TiArrowView;
import com.oxgcp.viewPager.PagerViewProxy;
import com.oxgcp.viewPager.TiUIScrollView.TiScrollViewLayout;

import android.graphics.Point;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewCompat;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Gravity;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
// import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;

public class PagerView extends TiUIView
{
	private static final String TAG = "TiUIScrollableView";

	private static final int PAGE_LEFT = 200;
	private static final int PAGE_RIGHT = 201;

	private final ViewPager mPager;
	private final ArrayList<TiViewProxy> mViews;
	private final ViewPagerAdapter mAdapter;
	private final TiCompositeLayout mContainer;
	// private final RelativeLayout mPagingControl;
	
	public float mContentWidth = 0;
	public float mContentHeight = 0;

	private int mCurIndex = 0;
	private boolean mEnabled = true;

	public PagerView(PagerViewProxy proxy)
	{
		super(proxy);
		Activity activity = proxy.getActivity();
		mViews = new ArrayList<TiViewProxy>();
		mAdapter = new ViewPagerAdapter(activity, mViews);
		mPager = buildViewPager(activity, mAdapter);
		
		mContainer = new TiViewPagerLayout(activity);
		mContainer.addView(mPager, buildFillLayoutParams());

		setNativeView(mContainer);


		// ViewCompat.setOverScrollMode(mPager,2);
		// mPager.setOffscreenPageLimit(3);
		// mPager.setPageMargin(15);
		// mPager.setClipChildren(false);
		// mContainer.setClipChildren(false);
	}

	private ViewPager buildViewPager(Context context, ViewPagerAdapter adapter)
	{
		ViewPager pager = (new ViewPager(context)
		{
			@Override
			public boolean onTouchEvent(MotionEvent event) {
				if (mEnabled) {
					return super.onTouchEvent(event);
				}

				return false;
			}

			@Override
			public boolean onInterceptTouchEvent(MotionEvent event) {
				if (mEnabled) {
					return super.onInterceptTouchEvent(event);
				}

				return false;
			}
		});

		pager.setAdapter(adapter);
		pager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener()
		{
			private boolean isValidScroll = false;
			private boolean justFiredDragEnd = false;

			boolean mNeedsRedraw = false;


			@Override
			public void onPageScrollStateChanged(int scrollState)
			{
				if ((scrollState == ViewPager.SCROLL_STATE_IDLE) && isValidScroll) {
					int oldIndex = mCurIndex;

					if (mCurIndex >= 0) {
						if (oldIndex >=0 && oldIndex != mCurIndex && oldIndex < mViews.size()) {
							// Don't know what these focused and unfocused
							// events are good for, but they were in our previous
							// scrollable implementation.
							// cf. https://github.com/appcelerator/titanium_mobile/blob/20335d8603e2708b59a18bafbb91b7292278de8e/android/modules/ui/src/ti/modules/titanium/ui/widget/TiScrollableView.java#L260
							TiEventHelper.fireFocused(mViews.get(oldIndex));
						}

						TiEventHelper.fireUnfocused(mViews.get(mCurIndex));
						if (oldIndex >= 0) {
							// oldIndex will be -1 if the view has just
							// been created and is setting currentPage
							// to something other than 0. In that case we
							// don't want a `scrollend` to fire.
							((PagerViewProxy)proxy).fireScrollEnd(mCurIndex, mViews.get(mCurIndex));
						}

						// if (shouldShowPager()) {
						// 	showPager();
						// }
					}

					// If we don't use this state variable to check if it's a valid
					// scroll, this event will fire when the view is first created
					// because on creation, the scroll state is initialized to 
					// `idle` and this handler is called.
					isValidScroll = false;
				} else if (scrollState == ViewPager.SCROLL_STATE_SETTLING) {
					((PagerViewProxy)proxy).fireDragEnd(mCurIndex, mViews.get(mCurIndex));

					// Note that we just fired a `dragend` so the `onPageSelected`
					// handler below doesn't fire a `scrollend`.  Read below comment.
					justFiredDragEnd = true;
				}

				mNeedsRedraw = (scrollState != ViewPager.SCROLL_STATE_IDLE);
			}
			
			@Override
			public void onPageSelected(int page)
			{

				// If we didn't just fire a `dragend` event then this is the case
				// where a user drags the view and settles it on a different view.
				// Since the OS settling logic is never run, the
				// `onPageScrollStateChanged` handler is never run, and therefore
				// we forgot to inform the Javascripters that the user just scrolled
				// their thing.

				if (!justFiredDragEnd && mCurIndex != -1) {
					((PagerViewProxy)proxy).fireScrollEnd(mCurIndex, mViews.get(mCurIndex));

					// if (shouldShowPager()) {
					// 	showPager();
					// }
				}
			}

			@Override
			public void onPageScrolled(int positionRoundedDown, float positionOffset, int positionOffsetPixels)
			{
				isValidScroll = true;

				// When we touch and drag the view and hold it inbetween the second
				// and third sub-view, this function will have been called with values
				// similar to:
				//		positionRoundedDown:	1
				//		positionOffset:			 0.5
				// ie, the first parameter is always rounded down; the second parameter
				// is always just an offset between the current and next view, it does
				// not take into account the current view.

				// If we add positionRoundedDown to positionOffset, positionOffset will
				// have the 'correct' value; ie, will be a natural number when we're on
				// one particular view, something.5 when inbetween views, etc.
				float positionFloat = positionOffset + positionRoundedDown;

				// `positionFloat` can now be used to calculate the correct value for
				// the current index. We add 0.5 so that positionFloat will be rounded
				// half up; ie, if it has a value of 1.5, it will be rounded up to 2; if
				// it has a value of 1.4, it will be rounded down to 1.
				mCurIndex = (int) Math.floor(positionFloat + 0.5);
				((PagerViewProxy)proxy).fireScroll(mCurIndex, positionFloat, mViews.get(mCurIndex));

				// Note that we didn't just fire a `dragend`.  See the above comment
				// in `onPageSelected`.
				justFiredDragEnd = false;

				if (mNeedsRedraw) mContainer.invalidate();
			}
		});
		return pager;
	}

	private TiCompositeLayout.LayoutParams buildFillLayoutParams()
	{
		TiCompositeLayout.LayoutParams params = new TiCompositeLayout.LayoutParams();
		params.autoFillsHeight = true;
		params.autoFillsWidth = false;
		return params;
	}

	@Override
	public void processProperties(KrollDict d)
	{
		if (d.containsKey(TiC.PROPERTY_VIEWS)) {
			setViews(d.get(TiC.PROPERTY_VIEWS));
		} 

		if (d.containsKey(TiC.PROPERTY_CURRENT_PAGE)) {
			int page = TiConvert.toInt(d, TiC.PROPERTY_CURRENT_PAGE);
			if (page > 0) {
				setCurrentPage(page);
			}
		}

		if (d.containsKey(TiC.PROPERTY_SCROLLING_ENABLED)) {
			mEnabled = TiConvert.toBoolean(d, TiC.PROPERTY_SCROLLING_ENABLED);
		}
		
		if (d.containsKey(TiC.PROPERTY_CONTENT_HEIGHT )) {
			mContentHeight = TiConvert.toFloat(d, TiC.PROPERTY_CONTENT_HEIGHT );
			setContentHeight(mContentHeight);
		}
		
		if (d.containsKey(TiC.PROPERTY_CONTENT_WIDTH )) {
			mContentWidth = TiConvert.toFloat(d, TiC.PROPERTY_CONTENT_WIDTH );
			setContentWidth(mContentWidth);
		}
		
		// if (d.containsKey(TiC.PROPERTY_SCROLL_TYPE)) {
		// 	if (Build.VERSION.SDK_INT >= 9) {
		// 		mPager.setOverScrollMode(TiConvert.toInt(d.get(TiC.PROPERTY_SCROLL_TYPE), View.OVER_SCROLL_ALWAYS));
		// 	}
		// }

		super.processProperties(d);

	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue,
			KrollProxy proxy)
	{
		if (TiC.PROPERTY_CURRENT_PAGE.equals(key)) {
			setCurrentPage(TiConvert.toInt(newValue));
		} else if (TiC.PROPERTY_SCROLLING_ENABLED.equals(key)) {
			mEnabled = TiConvert.toBoolean(newValue);
		// } else if (TiC.PROPERTY_SCROLL_TYPE.equals(key)){
		// 	if (Build.VERSION.SDK_INT >= 9) {
		// 		mPager.setOverScrollMode(TiConvert.toInt(newValue, View.OVER_SCROLL_ALWAYS));
		// 	}
		} else {
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}

	public void addView(TiViewProxy proxy)
	{
		if (!mViews.contains(proxy)) {
			mViews.add(proxy);
			getProxy().setProperty(TiC.PROPERTY_VIEWS, mViews.toArray());
			mAdapter.notifyDataSetChanged();
		}
	}

	public void removeView(TiViewProxy proxy)
	{
		if (mViews.contains(proxy)) {
			mViews.remove(proxy);
			getProxy().setProperty(TiC.PROPERTY_VIEWS, mViews.toArray());
			mAdapter.notifyDataSetChanged();
		}
	}
	
	public void setOverScrollMode(Object overScrollMode)
	{
			boolean scrollmode = TiConvert.toBoolean(overScrollMode);
			if (scrollmode == true) {
				ViewCompat.setOverScrollMode(mPager,0);
			}
			else {
				ViewCompat.setOverScrollMode(mPager,2);
			}
	}
	
	public void setHorizontalFadingEdgeEnabled(Object horizontalFadingEdgeEnabled)
	{
		mPager.setHorizontalFadingEdgeEnabled(TiConvert.toBoolean(horizontalFadingEdgeEnabled));
	}
	
	public void setOffscreenPageLimit(Object offscreenPageLimit)
	{
		mPager.setOffscreenPageLimit(TiConvert.toInt(offscreenPageLimit));
	}
	
	public void setPageMargin(Object pageMargin)
	{
		mPager.setPageMargin(TiConvert.toInt(pageMargin));
	}
	
	public void setContentWidth(Object contentWidth)
	{
		
		mContentWidth = TiConvert.toFloat(contentWidth);
		
		TiCompositeLayout.LayoutParams params = buildFillLayoutParams();
		
		if (mContentHeight > 0) {
			params.autoFillsHeight = false;
			params.optionHeight = new TiDimension(mContentHeight, 7);
		}
		
		params.autoFillsWidth = false;
		params.optionWidth = new TiDimension(mContentWidth, 6);
		
		mPager.setLayoutParams(params);
	}
	
	public void setContentHeight(Object contentHeight)
	{	
		
		mContentHeight = TiConvert.toFloat(contentHeight);
		
		TiCompositeLayout.LayoutParams params = buildFillLayoutParams();
		
		if (mContentWidth > 0) {
			params.autoFillsWidth = false;
			params.optionWidth = new TiDimension(mContentWidth, 6);
		}
		
		params.autoFillsHeight = false;
		params.optionHeight = new TiDimension(mContentHeight, 7);
		
		mPager.setLayoutParams(params);
	}
	
	public void setClipChildren(Object clipChildren)
	{
		mPager.setClipChildren(TiConvert.toBoolean(clipChildren));
		mContainer.setClipChildren(TiConvert.toBoolean(clipChildren));
	}
	
	public void moveNext()
	{
		move(mCurIndex + 1);
	}

	public void movePrevious()
	{
		move(mCurIndex - 1);
	}

	private void move(int index)
	{
		if (index < 0 || index >= mViews.size()) {
			Log.w(TAG, "Request to move to index " + index+ " ignored, as it is out-of-bounds.");
			return;
		}
		mCurIndex = index;
		mPager.setCurrentItem(index);
	}

	public void scrollTo(Object view)
	{
		if (view instanceof Number) {
			move(((Number) view).intValue());
		} else if (view instanceof TiViewProxy) {
			move(mViews.indexOf(view));
		}
	}
	
	public int getCurrentPage()
	{
		return mCurIndex;
	}

	public void setCurrentPage(Object view)
	{
		scrollTo(view);
	}

	public void setEnabled(Object value)
	{
		mEnabled = TiConvert.toBoolean(value);
	}

	public boolean getEnabled()
	{
		return mEnabled;
	}

	private void clearViewsList()
	{
		if (mViews == null || mViews.size() == 0) {
			return;
		}
		for (TiViewProxy viewProxy : mViews) {
			viewProxy.releaseViews();
		}
		mViews.clear();
	}

	public void setViews(Object viewsObject)
	{
		boolean changed = false;
		clearViewsList();

		if (viewsObject instanceof Object[]) {
			Object[] views = (Object[])viewsObject;
			for (int i = 0; i < views.length; i++) {
				if (views[i] instanceof TiViewProxy) {
					TiViewProxy tv = (TiViewProxy)views[i];
					mViews.add(tv);
					changed = true;
				}
			}
		}
		if (changed) {
			mAdapter.notifyDataSetChanged();
		}
	}

	public ArrayList<TiViewProxy> getViews()
	{
		return mViews;
	}

	@Override
	public void release()
	{
		if (mPager != null) {
			for (int i = mPager.getChildCount() - 1; i >=  0; i--) {
				mPager.removeViewAt(i);
			}
		}
		if (mViews != null) {
			for (TiViewProxy viewProxy : mViews) {
				viewProxy.releaseViews();
			}
			mViews.clear();
		}
		super.release();
	}





	public static class ViewPagerAdapter extends PagerAdapter
	{
		private final ArrayList<TiViewProxy> mViewProxies;
		public float mPageWidth = 1.0f;
		
		public ViewPagerAdapter(Activity activity, ArrayList<TiViewProxy> viewProxies)
		{
			mViewProxies = viewProxies;
		}

		@Override
		public void destroyItem(View container, int position, Object object)
		{
			((ViewPager) container).removeView((View) object);
			if (position < mViewProxies.size()) {
				TiViewProxy proxy = mViewProxies.get(position);
				proxy.releaseViews();
			}
		}

		@Override
		public void finishUpdate(View container) {}
		
		// @Override
    public void setPageWidth(float pageWidth) {
      mPageWidth = pageWidth;
    }
		
		@Override
    public float getPageWidth(int position) {
      return(mPageWidth);
    }
		
		@Override
		public int getCount()
		{
			return mViewProxies.size();
		}

		@Override
		public Object instantiateItem(View container, int position)
		{
			ViewPager pager = (ViewPager) container;
			TiViewProxy tiProxy = mViewProxies.get(position);
			TiUIView tiView = tiProxy.getOrCreateView();
			View view = tiView.getNativeView();
			
			if (view.getParent() != null) {
				pager.removeView(view);
			}
			if (position < pager.getChildCount()) {
				pager.addView(view, position);
			} else {
				pager.addView(view);
			}
			return view;
		}

		@Override
		public boolean isViewFromObject(View view, Object obj)
		{
			return (obj instanceof View && view.equals(obj));
		}

		@Override
		public void restoreState(Parcelable state, ClassLoader loader) {}

		@Override
		public Parcelable saveState() {return null;}

		@Override
		public void startUpdate(View container) {}

		@Override
		public int getItemPosition(Object object)
		{
			if (!mViewProxies.contains(object)) {
				return POSITION_NONE;
			} else {
				return POSITION_UNCHANGED;
			}
		}
	}






	public class TiViewPagerLayout extends TiCompositeLayout
	{
		public TiViewPagerLayout(Context context)
		{
			super(context, proxy);
			setFocusable(true);
			setFocusableInTouchMode(true);
			setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
		}
		
		private Point mCenter = new Point();
    private Point mInitialTouch = new Point();
		
		@Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mCenter.x = w / 2;
        mCenter.y = h / 2;
    }

		@Override
    public boolean onTouchEvent(MotionEvent ev) {
        //We capture any touches not already handled by the ViewPager
        // to implement scrolling from a touch outside the pager bounds.
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mInitialTouch.x = (int)ev.getX();
                mInitialTouch.y = (int)ev.getY();
            default:
                ev.offsetLocation(mCenter.x - mInitialTouch.x, mCenter.y - mInitialTouch.y);
                break;
        }

        return mPager.dispatchTouchEvent(ev);
    }
		
		@Override
		public boolean dispatchTouchEvent(MotionEvent ev)
		{
			// If the parent is a scroll view, then we prevent the scroll view from intercepting touch events
			if (getParent() instanceof TiScrollViewLayout) {
				int action = ev.getAction();
				switch (action) {
					case MotionEvent.ACTION_DOWN:
						requestDisallowInterceptTouchEvent(true);
						break;

					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						requestDisallowInterceptTouchEvent(false);
						break;
				}
			}
			return super.dispatchTouchEvent(ev);
		}
		
		@Override
		public boolean dispatchKeyEvent(KeyEvent event)
		{
			boolean handled = false;
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				switch (event.getKeyCode()) {
					case KeyEvent.KEYCODE_DPAD_LEFT: {
						movePrevious();
						handled = true;
						break;
					}
					case KeyEvent.KEYCODE_DPAD_RIGHT: {
						moveNext();
						handled = true;
						break;
					}
				}
			}
			return handled || super.dispatchKeyEvent(event);
		}
	}
}
