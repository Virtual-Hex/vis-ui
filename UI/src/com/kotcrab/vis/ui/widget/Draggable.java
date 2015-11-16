/*
 * Copyright 2014-2015 See AUTHORS file.
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


package com.kotcrab.vis.ui.widget;

import java.util.Iterator;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.utils.Disableable;
import com.kotcrab.vis.ui.layout.DragPane;

/** Draws copies of dragged actors which have this listener attached.
 *
 * @author MJ
 * @since 0.9.3 */
public class Draggable extends InputListener {
	private static final Vector2 MIMIC_COORDINATES = new Vector2();
	private static final Vector2 STAGE_COORDINATES = new Vector2();

	/** Initial fading time value of dragged actors.
	 *
	 * @see #setFadingTime(float) */
	public static float DEFAULT_FADING_TIME = 0.1f;
	/** Initial invisibility setting of dragged actors.
	 *
	 * @see #setInvisibleWhenDragged(boolean) */
	public static boolean INVISIBLE_ON_DRAG = false;
	/** Initial setting of keeping the dragged widget within its parent's bounds.
	 *
	 * @see #setKeepWithinParent(boolean) */
	public static boolean KEEP_WITHIN_PARENT = false;
	/** Initial alpha setting of dragged actors.
	 *
	 * @see #setAlpha(float) */
	public static float DEFAULT_ALPHA = 1f;
	/** Initial listener of draggables, unless a different listener is specified in the constructor. By default,
	 * {@link DragPane.DefaultDragListener} is used, which allows to drag actors into {@link DragPane} widgets.
	 *
	 * @see #setListener(DragListener)
	 * @see DragListener */
	public static DragListener DEFAULT_LISTENER = new DragPane.DefaultDragListener();

	// Settings.
	private DragListener listener;
	private boolean invisibleWhenDragged = INVISIBLE_ON_DRAG;
	private boolean keepWithinParent = KEEP_WITHIN_PARENT;
	private float fadingTime = DEFAULT_FADING_TIME;
	private float alpha = DEFAULT_ALPHA;
	private Interpolation fadingInterpolation = Interpolation.fade;
	private Interpolation movingInterpolation = Interpolation.sineOut;

	// Control variables.
	private final MimicActor mimic = new MimicActor();
	private float dragStartX;
	private float dragStartY;
	private float offsetX;
	private float offsetY;

	/** Creates a new draggable with default listener. */
	public Draggable () {
		this(DEFAULT_LISTENER);
	}

	/** @param listener is being notified of draggable events and can change its behavior. Can be null. */
	public Draggable (final DragListener listener) {
		this.listener = listener;
	}

	/** @param actor will have this listener attached and all other {@link Draggable} listeners removed. If you want multiple
	 *           {@link Draggable} listeners or you are sure that the widget has no other {@link Draggable}s attached, you can add
	 *           the listener using the standard method: {@link Actor#addListener(EventListener)} - avoiding validation and
	 *           iteration over actor's listeners. */
	public void attachTo (final Actor actor) {
		for (final Iterator<EventListener> listeners = actor.getListeners().iterator(); listeners.hasNext();) {
			final EventListener listener = listeners.next();
			if (listener instanceof Draggable) {
				listeners.remove();
			}
		}
		actor.addListener(this);
	}

	/** @return alpha color value of dragged actor copy. */
	public float getAlpha () {
		return alpha;
	}

	/** @param alpha alpha color value of dragged actor copy. */
	public void setAlpha (final float alpha) {
		this.alpha = alpha;
	}

	/** @return if true, original actor is invisible while it's being dragged. */
	public boolean isInvisibleWhenDragged () {
		return invisibleWhenDragged;
	}

	/** @param invisibleWhenDragged if true, original actor is invisible while it's being dragged. */
	public void setInvisibleWhenDragged (final boolean invisibleWhenDragged) {
		this.invisibleWhenDragged = invisibleWhenDragged;
	}

	/** @return if true, widget cannot be dragged out of the bounds of its parent. */
	public boolean isKeptWithinParent () {
		return keepWithinParent;
	}

	/** @param keepWithinParent if true, widget cannot be dragged out of the bounds of its parent. Stage coordinates in listener
	 *           will always be inside the parent. Note that for this setting to work properly, both actor and its parent have to
	 *           correctly return their sizes with {@link Actor#getWidth()} and {@link Actor#getHeight()} methods. */
	public void setKeepWithinParent (final boolean keepWithinParent) {
		this.keepWithinParent = keepWithinParent;
	}

	/** @return time after which the dragged actor copy disappears. */
	public float getFadingTime () {
		return fadingTime;
	}

	/** @param fadingTime time after which the dragged actor copy disappears. */
	public void setFadingTime (final float fadingTime) {
		this.fadingTime = fadingTime;
	}

	/** @param movingInterpolation used to move the dragged widgets to the original position when their drag was cancelled. */
	public void setMovingInterpolation (final Interpolation movingInterpolation) {
		this.movingInterpolation = movingInterpolation;
	}

	/** @param fadingInterpolation used to fade out dragged widgets after their drag was accepted. */
	public void setFadingInterpolation (final Interpolation fadingInterpolation) {
		this.fadingInterpolation = fadingInterpolation;
	}

	/** @param listener is being notified of draggable events and can change its behavior. Can be null.
	 * @see DragAdapter */
	public void setListener (final DragListener listener) {
		this.listener = listener;
	}

	/** @return listener notified of draggable events. Can be null. */
	public DragListener getListener () {
		return listener;
	}

	@Override
	public boolean touchDown (final InputEvent event, final float x, final float y, final int pointer, final int button) {
		final Actor actor = event.getListenerActor();
		if (actor instanceof Disableable && ((Disableable)actor).isDisabled()) {
			return false;
		}
		if (listener == null || listener.onStart(actor, event.getStageX(), event.getStageY())) {
			attachMimic(actor, event, x, y);
			return true;
		}
		return false;
	}

	private void attachMimic (final Actor actor, final InputEvent event, final float x, final float y) {
		mimic.clearActions();
		mimic.getColor().a = alpha;
		mimic.setActor(actor);
		offsetX = -x;
		offsetY = -y;
		getStageCoordinates(event);
		dragStartX = MIMIC_COORDINATES.x;
		dragStartY = MIMIC_COORDINATES.y;
		mimic.setPosition(dragStartX, dragStartY);
		actor.getStage().addActor(mimic);
		actor.setVisible(!invisibleWhenDragged);
	}

	/** @param event will extract stage coordinates from the event, respecting mimic offset and other dragging settings. */
	protected void getStageCoordinates (final InputEvent event) {
		if (keepWithinParent) {
			final Actor parent = mimic.getActor().getParent();
			if (parent != null) {
				MIMIC_COORDINATES.set(Vector2.Zero);
				parent.localToStageCoordinates(MIMIC_COORDINATES);
				final float parentX = MIMIC_COORDINATES.x;
				final float parentY = MIMIC_COORDINATES.y;
				final float parentEndX = parentX + parent.getWidth();
				final float parentEndY = parentY + parent.getHeight();
				MIMIC_COORDINATES.set(event.getStageX() + offsetX, event.getStageY() + offsetY);
				if (MIMIC_COORDINATES.x < parentX) {
					MIMIC_COORDINATES.x = parentX;
				} else if (MIMIC_COORDINATES.x + mimic.getWidth() > parentEndX) {
					MIMIC_COORDINATES.x = parentEndX - mimic.getWidth();
				}
				if (MIMIC_COORDINATES.y < parentY) {
					MIMIC_COORDINATES.y = parentY;
				} else if (MIMIC_COORDINATES.y + mimic.getHeight() > parentEndY) {
					MIMIC_COORDINATES.y = parentEndY - mimic.getHeight();
				}
				STAGE_COORDINATES.set(MathUtils.clamp(event.getStageX(), parentX, parentEndX - 1f),
					MathUtils.clamp(event.getStageY(), parentY, parentEndY - 1f));
			}
		} else {
			MIMIC_COORDINATES.set(event.getStageX() + offsetX, event.getStageY() + offsetY);
			STAGE_COORDINATES.set(event.getStageX(), event.getStageY());
		}
	}

	@Override
	public void touchDragged (final InputEvent event, final float x, final float y, final int pointer) {
		if (isDragged()) {
			getStageCoordinates(event);
			mimic.setPosition(MIMIC_COORDINATES.x, MIMIC_COORDINATES.y);
			if (listener != null) {
				listener.onDrag(mimic.getActor(), STAGE_COORDINATES.x, STAGE_COORDINATES.y);
			}
		}
	}

	@Override
	public void touchUp (final InputEvent event, final float x, final float y, final int pointer, final int button) {
		if (isDragged()) {
			getStageCoordinates(event);
			mimic.setPosition(MIMIC_COORDINATES.x, MIMIC_COORDINATES.y);
			if (listener == null || mimic.getActor().getStage() != null
				&& listener.onEnd(mimic.getActor(), STAGE_COORDINATES.x, STAGE_COORDINATES.y)) {
				// Drag end approved - fading out.
				addMimicHidingAction(Actions.fadeOut(fadingTime, fadingInterpolation));
			} else {
				// Drag end cancelled - returning to the original position.
				addMimicHidingAction(Actions.moveTo(dragStartX, dragStartY, fadingTime, movingInterpolation));
			}
		}
	}

	private boolean isDragged () {
		return mimic.getActor() != null;
	}

	private void addMimicHidingAction (final Action hidingAction) {
		mimic.addAction(Actions.sequence(hidingAction, Actions.removeActor()));
		mimic.getActor().addAction(Actions.delay(fadingTime, Actions.visible(true)));
	}

	/** Allows to control {@link Draggable} behavior.
	 *
	 * @author MJ
	 * @since 0.9.3 */
	public static interface DragListener {
		/** Use in listner's method for code clarity. */
		boolean CANCEL = false, APPROVE = true;

		/** @param actor is about to be dragged.
		 * @param stageX stage coordinate on X axis where the drag started.
		 * @param stageY stage coordinate on Y axis where the drag started.
		 * @return if true, actor will not be dragged. */
		boolean onStart (Actor actor, float stageX, float stageY);

		/** @param actor is being dragged.
		 * @param stageX stage coordinate on X axis with current cursor position.
		 * @param stageY stage coordinate on Y axis with current cursor position. */
		void onDrag (Actor actor, float stageX, float stageY);

		/** @param actor is about to stop being dragged.
		 * @param stageX stage coordinate on X axis where the drag ends.
		 * @param stageY stage coordinate on X axis where the drag ends.
		 * @return if true, "mirror" of the actor will quickly fade out. If false, mirror will return to the original actor's
		 *         position. */
		boolean onEnd (Actor actor, float stageX, float stageY);
	}

	/** Default, empty implementation of {@link DragListener}. Approves all drag requests.
	 *
	 * @author MJ
	 * @since 0.9.3 */
	public static class DragAdapter implements DragListener {
		@Override
		public boolean onStart (final Actor actor, final float stageX, final float stageY) {
			return APPROVE;
		}

		@Override
		public void onDrag (final Actor actor, final float stageX, final float stageY) {
		}

		@Override
		public boolean onEnd (final Actor actor, final float stageX, final float stageY) {
			return APPROVE;
		}
	}

	/** Draws the chosen actor with modified alpha value in a custom position. Clears mimicked actor upon removing from the stage.
	 *
	 * @author MJ
	 * @since 0.9.3 */
	public static class MimicActor extends Actor {
		private static final Vector2 LAST_POSITION = new Vector2();
		private Actor actor;

		public MimicActor () {
		}

		/** @param actor will be mimicked. */
		public MimicActor (final Actor actor) {
			this.actor = actor;
		}

		@Override
		public boolean remove () {
			actor = null;
			return super.remove();
		}

		/** @return mimicked actor. */
		public Actor getActor () {
			return actor;
		}

		/** @param actor will be mimicked. */
		public void setActor (final Actor actor) {
			this.actor = actor;
		}

		@Override
		public float getWidth () {
			return actor == null ? 0f : actor.getWidth();
		}

		@Override
		public float getHeight () {
			return actor == null ? 0f : actor.getHeight();
		}

		@Override
		public void draw (final Batch batch, final float parentAlpha) {
			if (actor != null) {
				LAST_POSITION.set(actor.getX(), actor.getY());
				actor.setPosition(getX(), getY());
				actor.draw(batch, getColor().a * parentAlpha);
				actor.setPosition(LAST_POSITION.x, LAST_POSITION.y);
			}
		}
	}
}
