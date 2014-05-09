# async-tools

__Tools to handle concurrent execution and asynchronous events__

This is a collection of tools for Java in general and Android in particular to manage asynchronous events.


## Requirements

* classes in `org.dmfs.asynctools.android` are Android specific and require the Android SDK

## Example code

### Join AsyncTasks in Android

AsyncTasks in Android can be used to execute short-running jobs in the background. Since the actual threads are hidden you can't join those AsyncTasks easily (to perfom another operation once all tasks have finished).

`JoinAsyncTasks` provides a convenient way to do this like so:

		JoinAsyncTask.join(new Runnable()
		{
			@Override
			public void run()
			{
				// do whatever you want to do when all AsyncTasks have finished.
			}
		}, asyncTask1, asyncTask2, asyncTask3);

This executes the given `Runnable` once `asyncTask1`, `asyncTask2` and `asyncTask3` have finished.

## License

Copyright (c) Marten Gajda 2014, licensed under Apache 2 (see `LICENSE`).
