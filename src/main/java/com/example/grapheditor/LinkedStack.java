package com.example.grapheditor;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Generic singly linked LIFO stack, used for the undo and redo history.
 */
public final class LinkedStack<T> {

    private static final class Entry<T> {
        final T value;
        final Entry<T> next;

        Entry(T value, Entry<T> next) {
            this.value = value;
            this.next = next;
        }
    }

    private Entry<T> top;
    private int size;

    public void push(T value) {
        top = new Entry<>(Objects.requireNonNull(value), top);
        size++;
    }

    public T peek() {
        if (top == null) {
            throw new NoSuchElementException("Empty stack");
        }
        return top.value;
    }

    public T pop() {
        T value = peek();
        top = top.next;
        size--;
        return value;
    }

    public boolean isEmpty() {
        return top == null;
    }

    public int size() {
        return size;
    }

    public void clear() {
        top = null;
        size = 0;
    }
}
