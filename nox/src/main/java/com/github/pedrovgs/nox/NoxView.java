/*
 * Copyright (C) 2015 Pedro Vicente Gomez Sanchez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.pedrovgs.nox;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import com.github.pedrovgs.nox.imageloader.ImageLoader;
import com.github.pedrovgs.nox.imageloader.ImageLoaderFactory;
import com.github.pedrovgs.nox.shape.Shape;
import com.github.pedrovgs.nox.shape.ShapeConfig;
import com.github.pedrovgs.nox.shape.ShapeFactory;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

/**
 * Main library component. This custom view receives a List of Nox objects and creates a awesome
 * panel full of images following different shapes. This new UI component is going to be similar to
 * the Apple's watch main menu user interface.
 *
 * NoxItem objects are going to be used to render the user interface using the resource used
 * to render inside each element and a view holder.
 *
 * @author Pedro Vicente Gomez Sanchez.
 */
public class NoxView extends View {

  private NoxConfig noxConfig;
  private Shape shape;
  private Scroller scroller;
  private NoxItemCatalog noxItemCatalog;
  private Paint paint = new Paint();
  private boolean wasInvalidatedBefore;
  private OnNoxItemClickListener listener = OnNoxItemClickListener.EMPTY;
  private GestureDetectorCompat gestureDetector;
  private int defaultShapeKey;
  private boolean useCircularTransformation;

  public NoxView(Context context) {
    super(context);
    initializeNoxViewConfig(context, null, 0, 0);
  }

  public NoxView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initializeNoxViewConfig(context, attrs, 0, 0);
  }

  public NoxView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initializeNoxViewConfig(context, attrs, defStyleAttr, 0);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public NoxView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initializeNoxViewConfig(context, attrs, defStyleAttr, defStyleRes);
  }

  /**
   * Given a List<NoxItem> instances configured previously gets the associated resource to draw the
   * view.
   */
  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (noxItemCatalog == null) {
      wasInvalidatedBefore = false;
      return;
    }
    updateShapeOffset();
    for (int i = 0; i < noxItemCatalog.size(); i++) {
      if (shape.isItemInsideView(i)) {
        loadNoxItem(i);
        float left = shape.getXForItemAtPosition(i);
        float top = shape.getYForItemAtPosition(i);
        drawNoxItem(canvas, i, left, top);
      }
    }
    canvas.restore();
    wasInvalidatedBefore = false;
  }

  /**
   * Configures a of List<NoxItem> instances to draw this items.
   */
  public void showNoxItems(final List<NoxItem> noxItems) {
    this.post(new Runnable() {
      @Override public void run() {
        initializeNoxItemCatalog(noxItems);
        createShape();
        initializeScroller();
        refreshView();
      }
    });
  }

  /**
   * Used to notify when the data source has changed and is necessary to re draw the view.
   */
  public void notifyDataSetChanged() {
    if (noxItemCatalog != null) {
      noxItemCatalog.recreate();
      createShape();
      initializeScroller();
      refreshView();
    }
  }

  /**
   * Changes the Shape used to the one passed as argument. This method will refresh the view.
   */
  public void setShape(Shape shape) {
    validateShape(shape);

    this.shape = shape;
    this.shape.calculate();
    initializeScroller();
    resetScroll();
  }

  /**
   * Delegates touch events to the scroller instance initialized previously to implement the scroll
   * effect. If the scroller does not handle the MotionEvent NoxView will check if any NoxItem has
   * been clicked to notify a previously configured OnNoxItemClickListener.
   */
  @Override public boolean onTouchEvent(MotionEvent event) {
    super.onTouchEvent(event);
    boolean clickCaptured = processTouchEvent(event);
    boolean scrollCaptured = scroller != null && scroller.onTouchEvent(event);
    boolean singleTapCaptured = getGestureDetectorCompat().onTouchEvent(event);
    return clickCaptured || scrollCaptured || singleTapCaptured;
  }

  /**
   * Delegates computeScroll method to the scroller instance to implement the scroll effect.
   */
  @Override public void computeScroll() {
    super.computeScroll();
    if (scroller != null) {
      scroller.computeScroll();
    }
  }

  /**
   * Returns the minimum X position the view can get taking into account the scroll offset.
   */
  public int getMinX() {
    return scroller.getMinX();
  }

  /**
   * Returns the maximum X position the view can get taking into account the scroll offset.
   */
  public int getMaxX() {
    return scroller.getMaxX();
  }

  /**
   * Returns the minimum Y position the view can get taking into account the scroll offset.
   */
  public int getMinY() {
    return scroller.getMinY();
  }

  /**
   * Returns the minimum Y position the view can get taking into account the scroll offset.
   */
  public int getMaxY() {
    return scroller.getMaxY();
  }

  public int getOverSize() {
    return scroller.getOverSize();
  }

  /**
   * Returns the current Shape used in to draw this view.
   */
  public Shape getShape() {
    return shape;
  }

  /**
   * Configures a OnNoxItemClickListener instance to be notified when a NoxItem instance is
   * clicked.
   */
  public void setOnNoxItemClickListener(OnNoxItemClickListener listener) {
    validateListener(listener);
    this.listener = listener;
  }

  /**
   * Resets the scroll position to the 0,0.
   */
  public void resetScroll() {
    if (scroller != null) {
      scroller.reset();
      refreshView();
    }
  }

  /**
   * Controls visibility changes to pause or resume this custom view and avoid performance
   * problems.
   */
  @Override protected void onVisibilityChanged(View changedView, int visibility) {
    super.onVisibilityChanged(changedView, visibility);
    if (changedView != this) {
      return;
    }

    if (visibility == View.VISIBLE) {
      resume();
    } else {
      pause();
    }
  }

  /**
   * Release resources when the view has been detached from the window.
   */
  @Override protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    release();
  }

  /**
   * Given a NoxItem position try to load this NoxItem if the view is not performing a fast scroll
   * after a fling gesture.
   */
  private void loadNoxItem(int position) {
    if (!scroller.isScrollingFast()) {
      noxItemCatalog.load(position, useCircularTransformation);
    }
  }

  /**
   * Resumes NoxItemCatalog and adds a observer to be notified when a NoxItem is ready to be drawn.
   */
  private void resume() {
    noxItemCatalog.addObserver(catalogObserver);
    noxItemCatalog.resume();
  }

  /**
   * Pauses NoxItemCatalog and removes the observer previously configured.
   */
  private void pause() {
    noxItemCatalog.pause();
    noxItemCatalog.deleteObserver(catalogObserver);
  }

  /**
   * Releases NoxItemCatalog and removes the observer previously configured.
   */
  private void release() {
    noxItemCatalog.release();
    noxItemCatalog.deleteObserver(catalogObserver);
  }

  /**
   * Observer implementation used to be notified when a NoxItem has been loaded.
   */
  private Observer catalogObserver = new Observer() {
    @Override public void update(Observable observable, Object data) {
      Integer position = (Integer) data;
      boolean isNoxItemLoadedInsideTheView = shape != null && shape.isItemInsideView(position);
      if (isNoxItemLoadedInsideTheView) {
        refreshView();
      }
    }
  };

  /**
   * Tries to post a invalidate() event if another one was previously posted.
   */
  private void refreshView() {
    if (!wasInvalidatedBefore) {
      wasInvalidatedBefore = true;
      invalidate();
    }
  }

  private void initializeNoxItemCatalog(List<NoxItem> noxItems) {
    ImageLoader imageLoader = ImageLoaderFactory.getPicassoImageLoader(getContext());
    this.noxItemCatalog =
        new NoxItemCatalog(noxItems, (int) noxConfig.getNoxItemSize(), imageLoader);
    this.noxItemCatalog.setDefaultPlaceholder(noxConfig.getPlaceholder());
    this.noxItemCatalog.addObserver(catalogObserver);
  }

  private void initializeScroller() {
    scroller =
        new Scroller(this, shape.getMinX(), shape.getMaxX(), shape.getMinY(), shape.getMaxY(),
            shape.getOverSize());
  }

  /**
   * Checks the X and Y scroll offset to update that values into the Shape configured previously.
   */
  private void updateShapeOffset() {
    int offsetX = scroller.getOffsetX();
    int offsetY = scroller.getOffsetY();
    shape.setOffset(offsetX, offsetY);
  }

  /**
   * Draws a NoxItem during the onDraw method.
   */
  private void drawNoxItem(Canvas canvas, int position, float left, float top) {
    if (noxItemCatalog.isBitmapReady(position)) {
      Bitmap bitmap = noxItemCatalog.getBitmap(position);
      canvas.drawBitmap(bitmap, left, top, paint);
    } else if (noxItemCatalog.isDrawableReady(position)) {
      Drawable drawable = noxItemCatalog.getDrawable(position);
      drawNoxItemDrawable(canvas, (int) left, (int) top, drawable);
    } else if (noxItemCatalog.isPlaceholderReady(position)) {
      Drawable drawable = noxItemCatalog.getPlaceholder(position);
      drawNoxItemDrawable(canvas, (int) left, (int) top, drawable);
    }
  }

  /**
   * Draws a NoxItem drawable during the onDraw method given a canvas object and all the
   * information needed to draw the Drawable passed as parameter.
   */
  private void drawNoxItemDrawable(Canvas canvas, int left, int top, Drawable drawable) {
    if (drawable != null) {
      int itemSize = (int) noxConfig.getNoxItemSize();
      drawable.setBounds(left, top, left + itemSize, top + itemSize);
      drawable.draw(canvas);
    }
  }

  /**
   * Initializes a Shape instance given the NoxView configuration provided programmatically or
   * using XML styleable attributes.
   */
  private void createShape() {
    if (shape == null) {
      float firstItemMargin = noxConfig.getNoxItemMargin();
      float firstItemSize = noxConfig.getNoxItemSize();
      int viewHeight = getMeasuredHeight();
      int viewWidth = getMeasuredWidth();
      int numberOfElements = noxItemCatalog.size();
      ShapeConfig shapeConfig =
          new ShapeConfig(numberOfElements, viewWidth, viewHeight, firstItemSize, firstItemMargin);
      shape = ShapeFactory.getShapeByKey(defaultShapeKey, shapeConfig);
    } else {
      shape.setNumberOfElements(noxItemCatalog.size());
    }
    shape.calculate();
  }

  /**
   * Initializes a NoxConfig instance given the NoxView configuration provided programmatically or
   * using XML styleable attributes.
   */
  private void initializeNoxViewConfig(Context context, AttributeSet attrs, int defStyleAttr,
      int defStyleRes) {
    noxConfig = new NoxConfig();
    TypedArray attributes = context.getTheme()
        .obtainStyledAttributes(attrs, R.styleable.nox, defStyleAttr, defStyleRes);
    initializeNoxItemSize(attributes);
    initializeNoxItemMargin(attributes);
    initializeNoxItemPlaceholder(attributes);
    initializeShapeConfig(attributes);
    initializeTransformationConfig(attributes);
    attributes.recycle();
  }

  /**
   * Configures the nox item default size used in NoxConfig, Shape and NoxItemCatalog to draw nox
   * item instances during the onDraw execution.
   */
  private void initializeNoxItemSize(TypedArray attributes) {
    float noxItemSizeDefaultValue = getResources().getDimension(R.dimen.default_nox_item_size);
    float noxItemSize = attributes.getDimension(R.styleable.nox_item_size, noxItemSizeDefaultValue);
    noxConfig.setNoxItemSize(noxItemSize);
  }

  /**
   * Configures the nox item default margin used in NoxConfig, Shape and NoxItemCatalog to draw nox
   * item instances during the onDraw execution.
   */
  private void initializeNoxItemMargin(TypedArray attributes) {
    float noxItemMarginDefaultValue = getResources().getDimension(R.dimen.default_nox_item_margin);
    float noxItemMargin =
        attributes.getDimension(R.styleable.nox_item_margin, noxItemMarginDefaultValue);
    noxConfig.setNoxItemMargin(noxItemMargin);
  }

  /**
   * Configures the placeholder used if there is no another placeholder configured in the NoxItem
   * instances during the onDraw execution.
   */
  private void initializeNoxItemPlaceholder(TypedArray attributes) {
    Drawable placeholder = attributes.getDrawable(R.styleable.nox_item_placeholder);
    if (placeholder == null) {
      placeholder = getContext().getResources().getDrawable(R.drawable.ic_nox);
    }
    noxConfig.setPlaceholder(placeholder);
  }

  /**
   * Configures the Shape used to show the list of NoxItems.
   */
  private void initializeShapeConfig(TypedArray attributes) {
    defaultShapeKey =
        attributes.getInteger(R.styleable.nox_shape, ShapeFactory.FIXED_CIRCULAR_SHAPE_KEY);
  }

  /**
   * Configures the visual transformation applied to the NoxItem resources loaded, images
   * downloaded from the internet and resources loaded from the system.
   */
  private void initializeTransformationConfig(TypedArray attributes) {
    useCircularTransformation =
        attributes.getBoolean(R.styleable.nox_use_circular_transformation, true);
  }

  private void validateShape(Shape shape) {
    if (shape == null) {
      throw new NullPointerException("You can't pass a null Shape instance as argument.");
    }
    if (noxItemCatalog != null && shape.getNumberOfElements() != noxItemCatalog.size()) {
      throw new IllegalArgumentException(
          "The number of items in the Shape instance passed as argument doesn't match with "
              + "the current number of NoxItems.");
    }
  }

  private void validateListener(OnNoxItemClickListener listener) {
    if (listener == null) {
      throw new NullPointerException(
          "You can't configure a null instance of OnNoxItemClickListener as NoxView listener.");
    }
  }

  /**
   * Returns a GestureDetectorCompat lazy instantiated created to handle single tap events and
   * detect if a NoxItem has been clicked to notify the previously configured listener.
   */
  private GestureDetectorCompat getGestureDetectorCompat() {
    if (gestureDetector == null) {
      GestureDetector.OnGestureListener gestureListener = new SimpleOnGestureListener() {

        @Override public boolean onSingleTapUp(MotionEvent e) {
          boolean handled = false;
          int position = shape.getNoxItemHit(e.getX(), e.getY());
          if (position >= 0) {
            handled = true;
            NoxItem noxItem = noxItemCatalog.getNoxItem(position);
            listener.onNoxItemClicked(position, noxItem);
          }
          return handled;
        }
      };
      gestureDetector = new GestureDetectorCompat(getContext(), gestureListener);
    }
    return gestureDetector;
  }

  private boolean processTouchEvent(MotionEvent event) {
    if (shape == null) {
      return false;
    }

    boolean handled = false;
    float x = event.getX();
    float y = event.getY();
    int noxItemHit = shape.getNoxItemHit(x, y);
    boolean isNoxItemHit = noxItemHit >= 0;
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        if (isNoxItemHit) {
          changeNoxItemStateToPressed(noxItemHit);
          handled = true;
        }
        break;
      case MotionEvent.ACTION_CANCEL:
      case MotionEvent.ACTION_UP:
        for (int i = 0; i < noxItemCatalog.size(); i++) {
          if (shape.isItemInsideView(i)) {
            changeNoxItemStateToNotPressed(i);
            handled = true;
          }
        }
        break;
      default:
    }
    return handled;
  }

  private void changeNoxItemStateToPressed(int noxItemPosition) {
    int[] stateSet = new int[2];
    stateSet[0] = android.R.attr.state_pressed;
    stateSet[1] = android.R.attr.state_enabled;
    setNoxItemState(noxItemPosition, stateSet);
  }

  private void changeNoxItemStateToNotPressed(int noxItemPosition) {
    int[] stateSet = new int[2];
    stateSet[0] = -android.R.attr.state_pressed;
    stateSet[1] = android.R.attr.state_enabled;
    setNoxItemState(noxItemPosition, stateSet);
  }

  private void setNoxItemState(int noxItemPosition, int[] stateSet) {
    boolean refreshView = false;
    if (noxItemCatalog.isDrawableReady(noxItemPosition)) {
      Drawable drawable = noxItemCatalog.getDrawable(noxItemPosition);
      drawable.setState(stateSet);
      refreshView = true;
    } else if (noxItemCatalog.isPlaceholderReady(noxItemPosition)) {
      Drawable drawable = noxItemCatalog.getPlaceholder(noxItemPosition);
      drawable.setState(stateSet);
      refreshView = true;
    }
    if (refreshView) {
      refreshView();
    }
  }

  /**
   * Method created for testing purposes. Configures the Scroller to be used by NoxView.
   * This method is needed because we don't have access to the view constructor.
   */
  void setScroller(Scroller scroller) {
    this.scroller = scroller;
  }

  /**
   * Method created for testing purposes. Configures the NoxItemCatalog to be used by NoxView.
   * This method is needed because we don't have access to the view constructor.
   */
  void setNoxItemCatalog(NoxItemCatalog noxItemCatalog) {
    this.noxItemCatalog = noxItemCatalog;
  }

  /**
   * Method created for testing purposes. Returns the NoxItemCatalog to be used by NoxView.
   * This method is needed because we don't have access to the view constructor.
   */
  NoxItemCatalog getNoxItemCatalog() {
    return noxItemCatalog;
  }
}

