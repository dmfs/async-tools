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

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.util.Log;


/**
 * Join a number of {@link AsyncTask}s, i.e. wait for all of them to complete, and execute a callback. To use it simply call
 * {@link #join(Runnable, AsyncTask...)} like this:
 * 
 * <pre>
 * 	private final OnJoinAsyncTasksCallback onJoinTasks = new OnJoinAsyncTasksCallback()
 * 	{
 * 		{@literal @}Override
 * 		public void onJoinAsyncTasks(int id)
 * 		{
 * 			// do whatever you want to do when all AsyncTasks have finished.
 * 		}
 * 	};
 * 
 * 
 * 	...
 * 
 * 	private void someMethod()
 * 	{
 * 		...
 * 		JoinAsyncTask.join(onJoinTasks, asyncTask1, asyncTask2, asyncTask3);
 * 		...
 * 	}
 * </pre>
 * 
 * This executes the calls the callback once <code>asyncTask1</code>, <code>asyncTask2</code> and <code>asyncTask3</code> have finished.
 * <p>
 * <strong>Note:</strong> You need to ensure that you keep a reference to the callback, otherwise it might get garbage collected and not executed.
 * <code>JoinAsyncTask</code> only stores a {@link WeakReference} of the callback to avoid {@link Context} leaks. To be sure, just let your {@link Activity} or
 * {@link Fragment} implement {@link OnJoinAsyncTasksCallback} and pass it as callback. See below for an example:
 * </p>
 * 
 * <pre>
 * public class SomeActivity extends Activity implements OnJoinAsyncTasksCallback
 * {
 * 
 * 	...
 * 
 * 	{@literal @}Override
 * 	public void onJoinAsyncTasks(int id)
 * 	{
 * 		// do whatever you want to do when all AsyncTasks have finished.
 * 	}
 * 
 * 	...
 * 
 * 	private void someMethod()
 * 	{
 * 		...
 * 		JoinAsyncTask.join(this, 1, asyncTask1, asyncTask2, asyncTask3);
 * 		...
 * 	}
 * 
 * };
 * 
 * </pre>
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class JoinAsyncTask extends AsyncTask<AsyncTask<?, ?, ?>, Void, Boolean>
{
	private final static String TAG = "JoinAsyncTask";

	/**
	 * A callback that is called when all {@link AsyncTask}s have finished.
	 */
	public interface OnJoinAsyncTasksCallback
	{
		/**
		 * Called when all {@link AsyncTask} have finished.
		 * 
		 * @param id
		 *            The id passed to {@link JoinAsyncTask#join(OnJoinAsyncTasksCallback, int, AsyncTask...)} or
		 *            {@link JoinAsyncTask#JoinAsyncTask(OnJoinAsyncTasksCallback, int)} or <code>0</code> if called via
		 *            {@link JoinAsyncTask#join(OnJoinAsyncTasksCallback, AsyncTask...)}.
		 */
		public void onJoinAsyncTasks(int id);
	}

	private final WeakReference<OnJoinAsyncTasksCallback> mCallback;

	private final int mId;


	/**
	 * Waits for all given {@link AsyncTask}s to complete and calls the given {@link OnJoinAsyncTasksCallback}.
	 * 
	 * @param callback
	 *            The {@link OnJoinAsyncTasksCallback} to call when all {@link AsyncTask}s have finished. You need to ensure you still hold a reference to the
	 *            callback, otherwise it might not get executed.
	 * @param asyncTasks
	 *            The {@link AsyncTask}s to join.
	 * @param id
	 *            An identifier that is passed to the callback.
	 */
	public static void join(OnJoinAsyncTasksCallback callback, int id, AsyncTask<?, ?, ?>... asyncTasks)
	{
		if (VERSION.SDK_INT >= 11)
		{
			new JoinAsyncTask(callback, id).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, asyncTasks);
		}
		else
		{
			new JoinAsyncTask(callback, id).execute(asyncTasks);
		}
	}


	/**
	 * Waits for all given {@link AsyncTask}s to complete and calls the given {@link OnJoinAsyncTasksCallback}.
	 * 
	 * @param callback
	 *            The {@link OnJoinAsyncTasksCallback} to call when all {@link AsyncTask}s have finished. You need to ensure you still hold a reference to the
	 *            callback, otherwise it might not get executed.
	 * @param asyncTasks
	 *            The {@link AsyncTask}s to join.
	 */
	public static void join(OnJoinAsyncTasksCallback callback, AsyncTask<?, ?, ?>... asyncTasks)
	{
		join(callback, 0, asyncTasks);
	}


	/**
	 * Creates an {@link AsyncTask} that joins other {@link AsyncTask}s and calls the given callback once all the other {@link AsyncTask}s have completed. The
	 * callback method will be executed in the main tread.
	 * 
	 * @param callback
	 *            The callback to call when all AsyncTasks are completed.
	 * @param id
	 *            An identifier that is passed to the callback.
	 */
	public JoinAsyncTask(OnJoinAsyncTasksCallback callback, int id)
	{
		mCallback = new WeakReference<OnJoinAsyncTasksCallback>(callback);
		mId = id;
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
		OnJoinAsyncTasksCallback callback = mCallback.get();
		if (callback != null)
		{
			callback.onJoinAsyncTasks(mId);
		}
	}

}
