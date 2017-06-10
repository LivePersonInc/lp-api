package com.liveperson.api.infra.ws;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 *
 * @author eitanya
 */
public interface HandlerManager<T> {
    default HandlerManager getImpl() {
        return null;
    }

    default public Predicate<T> register(Predicate<T> matcher, Consumer<T> filter) {
        return getImpl().register(matcher, filter);
    }

    default public Predicate<T> unRegister(Predicate<T> registsredFilter) {
        return getImpl().unRegister(registsredFilter);
    }

    default public Predicate<T> filter() {
        return getImpl().filter();
    }
}
