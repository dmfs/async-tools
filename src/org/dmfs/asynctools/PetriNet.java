/*
 * Copyright (C) 2014 Marten Gajda <marten@dmfs.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package org.dmfs.asynctools;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


/**
 * A simple Petri net implementation. It keeps track of tokens on places and executes the action associated with a transition when they fire. This is meant to
 * manage concurrent processes and asynchronous events.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class PetriNet
{

	/**
	 * A transition taking a parameter of type T to execute. A transition can be fired when it's activated and it's activated when <em>all</em> input arcs are
	 * enabled. An input arc is enabled when the input place it connects to has the required number of tokens. Usually that means it has at least one token, but
	 * the actual required token count can be set by the multiplicity of the arc.
	 * <p>
	 * <strong>Note:<strong> The a multiplicity of <code>0</code> will require the place to have exactly <code>0</code> tokens to enable the arc. Be careful
	 * when setting such transitions to auto-fire. If multiple of these originate at the same place you might get an infinite loop.
	 * </p>
	 * 
	 * @param <T>
	 *            The type of the parameter of {@link #execute(Object)};
	 */
	public static class Transition<T>
	{
		/**
		 * Map of input {@link Place}s to the multiplicity of the arc to it.
		 */
		Map<Place, Integer> mInput = new HashMap<Place, Integer>();

		/**
		 * Map of output {@link Place}s to the multiplicity of the arc to it.
		 */
		Map<Place, Integer> mOutput = new HashMap<Place, Integer>();

		/**
		 * Indicates that the transition should fire automatically once it is activated (i.e. all input arcs are enabled because the connected places have the
		 * required numbers of tokens).
		 * <p>
		 * If a {@link Transition} fires automatically the data will be set to <code>null</code>;
		 * </p>
		 * <p>
		 * <strong>Note:<strong> You'll have to ensure yourself that no infinite loop will be entered, otherwise you'll get a stack overflow.
		 * </p>
		 */
		boolean mAutoFire = false;

		/**
		 * Indicates if there is a pending fire operation.
		 */
		boolean mPendingFire = false;

		/**
		 * The parameter for the pending fire operation.
		 */
		private T mData = null;


		/**
		 * Creates a Transition that is activated by default and can be fired exactly once.
		 * 
		 * @param outputMultiplicity
		 *            The output multiplicity for all given outputs. Use {@link #addOutputs(int, Place...)} to add outputs with different multiplicity values.
		 * @param outputs
		 *            The outputs.
		 */
		public Transition(int outputMultiplicity, Place... outputs)
		{
			this(1, new Place(1), outputMultiplicity, outputs);
		}


		public Transition(Place input, Place... outputs)
		{
			this(1, input, 1, outputs);
		}


		public Transition(Place input, int outputMultiplicity, Place... outputs)
		{
			this(1, input, outputMultiplicity, outputs);
		}


		/**
		 * Creates a Transition with the given inputs and outputs and their respective multiplicity values.
		 * <p>
		 * <strong>Note:</strong> An input with the multiplicity of <code>0</code> will be enabled only if the input place has exactly <code>0</code> tokens.
		 * </p>
		 * 
		 * @param inputMultiplicity
		 *            The input multiplicity for all given inputs. Use {@link #addInputs(int, Place...)} to add inputs with different multiplicity values;
		 * @param input
		 *            The inputs.
		 * @param outputMultiplicity
		 *            The output multiplicity for all given outputs. Use {@link #addOutputs(int, Place...)} to add outputs with different multiplicity values.
		 * @param outputs
		 *            The outputs.
		 */
		public Transition(int inputMultiplicity, Place input, int outputMultiplicity, Place... outputs)
		{
			mInput.put(input, inputMultiplicity);
			addOutputs(outputMultiplicity, outputs);
		}


		/**
		 * Add inputs that have the given multiplicity.
		 * <p>
		 * <strong>Note:</strong> An input with the multiplicity of <code>0</code> will be enabled only if the input place has exactly <code>0</code> tokens.
		 * </p>
		 * 
		 * @param inputMultiplicity
		 *            The multiplicity of the given inputs.
		 * @param inputs
		 *            The input places.
		 * @return This transition for chaining.
		 */
		public final Transition<?> addInputs(int inputMultiplicity, Place... inputs)
		{
			for (Place input : inputs)
			{
				mInput.put(input, inputMultiplicity);
			}
			return this;
		}


		/**
		 * Add outputs that have the given multiplicity.
		 * 
		 * @param outputMultiplicity
		 *            The multiplicity of the given outputs.
		 * @param outputs
		 *            The output places.
		 * @return This transition for chaining.
		 */
		public final Transition<?> addOutputs(int outputMultiplicity, Place... outputs)
		{
			for (Place output : outputs)
			{
				mOutput.put(output, outputMultiplicity);
			}
			return this;
		}


		/**
		 * Enable automatic fire on the transition. A transition that is set to fire automatically will fire as soon as it's activated (i.e. all input places
		 * have the required number of tokens). No additional call to {@link PetriNet#fire(Transition, Object)} will be necessary.
		 * 
		 * @param autoFire
		 *            <code>true</code> to enable auto fire, <code>false</code> to disable it.
		 * @return This transition for chaining.
		 */
		public Transition<T> setAutoFire(boolean autoFire)
		{
			mAutoFire = autoFire;
			mPendingFire |= autoFire;
			return this;
		}


		/**
		 * Fire this transition.
		 * 
		 * @param data
		 *            The data that has been passed to {@link PetriNet#fire(Transition, Object)} or <code>null</code> if this transition fired automatically.
		 * @return <code>true</code> if the transition is pending, <code>false</code> if it has been executed.
		 */
		boolean fire(T data)
		{
			Set<Entry<Place, Integer>> inputSet = mInput.entrySet();

			// check if we can fire
			for (Entry<Place, Integer> entry : inputSet)
			{
				int value = entry.getValue();
				int tokenCount = entry.getKey().mTokenCount;
				if ((value > 0 && tokenCount < value) || (value == 0 && tokenCount > 0))
				{
					mPendingFire = true;
					mData = data;
					return true;
				}
			}

			// remove tokens from input places
			for (Entry<Place, Integer> entry : inputSet)
			{
				entry.getKey().mTokenCount -= entry.getValue();
			}

			execute(data);

			// add tokens to output places
			for (Entry<Place, Integer> entry : mOutput.entrySet())
			{
				entry.getKey().mTokenCount += entry.getValue();
			}

			mPendingFire = mAutoFire;
			mData = null;
			return false;
		}


		/**
		 * Fire a pending Transition.
		 * 
		 * @return <code>true</code> if the transition is still pending because it's not activated.
		 * 
		 * @throws IllegalStateException
		 *             if this transition was not pending.
		 */
		boolean firePending()
		{
			if (mPendingFire)
			{
				return fire(mData);
			}
			else
			{
				throw new IllegalStateException("not fire pending");
			}
		}


		/**
		 * Execute the transition. If this transition was fired automatically <code>data</code> will be <code>null</code>. By default this method does nothing.
		 * Override it to perform any actions.
		 * 
		 * @param data
		 *            The data that was provided when the transition was fired.
		 */
		protected void execute(T data)
		{
			// nothing to be done by default.
		};
	}

	/**
	 * A place just holds a specific number of tokens. The number changed every time a connected transition is executed.
	 */
	public final static class Place
	{
		int mTokenCount;


		/**
		 * Create a place with <code>0</code> tokens.
		 */
		public Place()
		{
			mTokenCount = 0;
		}


		/**
		 * Create a new Place with an initial number of tokens.
		 * 
		 * @param initialTokenCount
		 *            The number of tokens this Place starts with.
		 */
		public Place(int initialTokenCount)
		{
			mTokenCount = initialTokenCount;
		}


		/**
		 * Returns the current token count on this place.
		 * 
		 * @return The current token count.
		 */
		public int getTokenCount()
		{
			return mTokenCount;
		}
	}

	/**
	 * A map of all transition that have a specific place as input.
	 */
	private final Map<Place, Set<Transition<?>>> mTransitionsByInput = new HashMap<PetriNet.Place, Set<Transition<?>>>(32);


	/**
	 * Initialize the {@link PetriNet} with the given {@link Transition}s.
	 * 
	 * @param transitions
	 *            All {@link Transition}s of this Petri net.
	 */
	public PetriNet(Transition<?>... transitions)
	{
		for (Transition<?> transition : transitions)
		{
			for (Place place : transition.mInput.keySet())
			{
				getOutgingTransitions(place).add(transition);
			}
		}
	}


	/**
	 * Creates a {@link PetriNet} from all the {@link Transition}s referenced by the given object. Note that this will only include {@link Transition}s defined
	 * in the actual class of this object. Transitions in inherited classes will be ignored.
	 * <p>
	 * <strong>Warning:</strong> Don't use to initialize a field of the object itself, since there is no guarantee that all {@link Transition}s have been
	 * instantiated yet. Always call it from the constructor or a method.
	 * </p>
	 * 
	 * @param container
	 *            The object that contains all the {@link Transition}.
	 */
	public PetriNet(Object container)
	{
		final Field[] fields = container.getClass().getDeclaredFields();
		final Class<?> transitionClass = Transition.class;
		for (Field field : fields)
		{
			if (transitionClass.isAssignableFrom(field.getType()))
			{
				field.setAccessible(true);

				try
				{
					Transition<?> transition = (Transition<?>) field.get(container);
					for (Place place : transition.mInput.keySet())
					{
						getOutgingTransitions(place).add(transition);
					}
				}
				catch (IllegalAccessException e)
				{
					// ignore
				}
			}
		}
	}


	/**
	 * Returns the set of outgoing transitions for a specific {@link Place}, i.e. all {@link Transition}s that have this place as an input.
	 * 
	 * @param place
	 *            The {@link Place}.
	 * @return A {@link Set} of {@link Transition}s, never <code>null</code>.
	 */
	private Set<Transition<?>> getOutgingTransitions(Place place)
	{
		Set<Transition<?>> result = mTransitionsByInput.get(place);
		if (result == null)
		{
			result = new HashSet<Transition<?>>();
			mTransitionsByInput.put(place, result);
		}
		return result;
	}


	/**
	 * Fire the given transition. If the transition is not activated yet, because not all input places have the required number of tokens, it will be pending
	 * until it can be fired. If this is called a second time one pending transition, <code>data</code> will override the data of the pending transition.
	 * <p>
	 * <strong>Note:</strong> At present all transitions that are fired, either explicitly or implicitly (because a pending transition became enabled or a
	 * transition fired automatically), will be executed in the calling thread. You need to ensure yourself that this won't cause any problems.
	 * </p>
	 * 
	 * @param transition
	 *            The transition to fire.
	 * @param data
	 *            The data to pass to {@link Transition#execute(Object)}.
	 * @return
	 */
	public synchronized <T> boolean fire(Transition<T> transition, T data)
	{
		boolean pending = transition.fire(data);

		if (!pending)
		{
			// fire all following transitions that are pending
			firePending(transition);
		}

		return pending;
	}


	/**
	 * Fire all pending transitions that might have been activated by the given transition.
	 * 
	 * @param transition
	 *            The transition that has just been fired.
	 */
	private void firePending(Transition<?> transition)
	{
		Set<Transition<?>> transitions = new HashSet<Transition<?>>();

		// get following transitions
		for (Place place : transition.mOutput.keySet())
		{
			Set<Transition<?>> next = mTransitionsByInput.get(place);
			if (next != null)
			{
				transitions.addAll(next);
			}
		}

		// get input transitions to auto fire any transition that fires automatically on 0 tokens
		for (Place place : transition.mInput.keySet())
		{
			if (place.getTokenCount() == 0)
			{
				Set<Transition<?>> next = mTransitionsByInput.get(place);
				if (next != null)
				{
					transitions.addAll(next);
				}
			}
		}

		// don't auto-fire ourself, that might lead to an infinite loop
		transitions.remove(transition);

		// fire all following transitions that are pending
		for (Transition<?> nextTransition : transitions)
		{
			if (nextTransition.mPendingFire)
			{
				if (!nextTransition.firePending())
				{
					firePending(nextTransition);
				}
			}
		}
	}
}
