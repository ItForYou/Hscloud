package com.victor.async.future;

public interface DependentFuture<T> extends Future<T>, DependentCancellable {
}
