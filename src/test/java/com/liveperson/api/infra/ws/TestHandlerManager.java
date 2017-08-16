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

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.Assert.*;

public class TestHandlerManager {

    @Test
    public void testRegisterAndUnregister() {
        HandlerManager<String> manager = new HandlerManagerImpl<>();
        AtomicInteger ai = new AtomicInteger();

        manager.filter().test("hello");
        assertEquals(0, ai.get());
        Predicate<String> helFilter = manager.register(s -> s.startsWith("hel"), s -> {
            ai.incrementAndGet();
        });
        manager.filter().test("hello");
        assertEquals(1, ai.get());
        manager.filter().test("world");
        assertEquals(1, ai.get());
        manager.unRegister(helFilter);
        manager.filter().test("hello");
        assertEquals(1, ai.get());
    }

    @Test
    public void testFilterExecution() {
        HandlerManager<String> manager = new HandlerManagerImpl<>();
        AtomicInteger ai = new AtomicInteger();
        manager.register(s -> s.startsWith("hel"), s -> ai.incrementAndGet());
        manager.filter().test("world");
        assertEquals(0, ai.get());
        manager.filter().test("hello");
        assertEquals(1, ai.get());
    }

    @Test
    public void testTwoFilterExecution() {
        HandlerManager<String> manager = new HandlerManagerImpl<>();
        AtomicInteger ai = new AtomicInteger();
        AtomicInteger ai1 = new AtomicInteger();
        manager.register(s -> s.equals("bel"), s -> {
            ai.incrementAndGet();
        });
        manager.register(s -> s.startsWith("hel"), s -> {
            ai1.incrementAndGet();
        });
        manager.register(s -> s.startsWith("hell"), s -> {
            ai1.incrementAndGet();
        });
        manager.filter().test("bel");
        assertEquals(1, ai.get());
        manager.filter().test("hello");
        assertEquals(2, ai1.get());
    }
}
