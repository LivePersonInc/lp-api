/**
 * The MIT License
 * Copyright (c) 2017 LivePerson, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

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
