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

package org.dmfs.asynctools.android;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutionException;

import android.os.AsyncTask;
import android.util.Log;


/**
 * Join a number of {@link AsyncTask}s, i.e. wait for all of them to complete, and execute a callback. To use it simply call
 * {@link #join(Runnable, AsyncTask...)} like this:
 * 
 * <pre>
 * 			JoinAsyncTask.join(new Runnable()
 * 			{
 * 				{@literal @}Override
 * 				public void run()
 * 				{
 * 					// do whatever you want to do when all AsyncTasks have finished.
 * 				}
 * 			}, asyncTask1, asyncTask2, asyncTask3);
 * </pre>
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class JoinAsyncTask extends AsyncTask<AsyncTask<?, ?, ?>, Void, Boolean>
{
	private final static String TAG = "JoinAsyncTask";

	private final WeakReference<Runnable> mCallback;


	/**
	 * Waits for all given {@link AsyncTask}s to complete and executes the given {@link Runnable}.
	 * 
	 * @param callback
	 *            The {@link Runnable} to execute when all {@link AsyncTask}s have finished.
	 * @param asyncTasks
	 *            The {@link AsyncTask} to join.
	 */
	public static void join(Runnable callback, AsyncTask<?, ?, ?>... asyncTasks)
	{
		new JoinAsyncTask(callback).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, asyncTasks);
	}


	/**
	 * Creates an {@link AsyncTask} that joins other {@link AsyncTask}s and calls the given callback once all the other {@link AsyncTask}s have completed. The
	 * callback will be executed in the main tread.
	 * 
	 * @param callback
	 *            The callback to execute when all AsyncTasks are completed.
	 */
	public JoinAsyncTask(Runnable callback)
	{
		mCallback = new WeakReference<Runnable>(callback);
	}


	@Override
	protected Boolean doInBackground(AsyncTask<?, ?, ?>... params)
	{
		for (AsyncTask<?, ?, ?> task : params)
		{
			if (task != null)
			{
				try
				{
					task.get();
				}
				catch (InterruptedException e)
				{
					Log.e(TAG, "interrupted", e);
				}
				catch (ExecutionException e)
				{
					Log.e(TAG, "can't get result", e);
				}
			}
		}
		return true;
	}


	@Override
	protected void onPostExecute(Boolean result)
	{
		Runnable callback = mCallback.get();
		if (callback != null)
		{
			callback.run();
		}
	}

}
