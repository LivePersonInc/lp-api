
package com.liveperson.api.infra.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 *
 * @author eitanya
 */
public class HandlerManagerImpl<T> implements HandlerManager<T> {
    private Map<Predicate<T>,Predicate<T>> filters;

    public HandlerManagerImpl() {
        this.filters = new ConcurrentHashMap<>();
    }

    @Override
    public Predicate<T> register(Predicate<T> matcher, Consumer<T> filter) {
        final Predicate<T> registeredFilter = p -> {
            if (matcher.test(p)) {
                filter.accept(p);
                return true;                
            }
            return false;
        };
        filters.put(matcher,registeredFilter);
        return registeredFilter;
    }

    @Override
    public Predicate<T> unRegister(Predicate<T> registsredMatcher) {
        return filters.remove(registsredMatcher);
    }

    @Override
    public Predicate<T> filter() {
        return p -> filters.values().stream().allMatch(filter -> filter.test(p));
    }
}
