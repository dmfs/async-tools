# async-tools

__Tools to handle concurrent execution and asynchronous events__

This is a collection of tools for Java in general and Android in particular to manage asynchronous events.


## Requirements

* classes in `org.dmfs.asynctools.android` are Android specific and require the Android SDK

## Example code

### Join AsyncTasks in Android

AsyncTasks in Android can be used to execute short-running jobs in the background. Since the actual threads are hidden you can't join those AsyncTasks easily (to perfom another operation once all tasks have finished).

`JoinAsyncTasks` provides a convenient way to do this like so:

		private final OnJoinAsyncTasksCallback onJoinTasks = new OnJoinAsyncTasksCallback()
		{
			@Override
			public void onJoinAsyncTasks(int id)
			{
				// do whatever you want to do when all AsyncTasks have finished.
			}
		};

		...

		private void someMethod()
		{
			...
			JoinAsyncTask.join(onJoinTasks, asyncTask1, asyncTask2, asyncTask3);
			...
		}

This executes the calls the callback once `asyncTask1`, `asyncTask2` and `asyncTask3` have finished.

**Note:** You need to ensure that you keep a reference to the callback, otherwise it might get garbage collected and not executed. `JoinAsyncTask` only stores a `WeakReference` of the callback to avoid Context leaks. To be sure, just let your Activity or Fragment implement `OnJoinAsyncTasksCallback` and pass it as callback. See below for an example:

		public class SomeActivity extends Activity implements OnJoinAsyncTasksCallback
		{

			...

			@Override
			public void onJoinAsyncTasks(int id)
			{
				// do whatever you want to do when all AsyncTasks have finished.
			}

			...

			private void someMethod()
			{
				...
				JoinAsyncTask.join(this, 1, asyncTask1, asyncTask2, asyncTask3);
				...
			}
		};


## License

Copyright (c) Marten Gajda 2014, licensed under Apache 2 (see `LICENSE`).
