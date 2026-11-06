package ics.util;

import java.util.*;

public final class Sorting {
    private Sorting() {
    }

    public static <T> void insertionSort(List<T> list, Comparator<T> order) {
        for (int i = 1; i < list.size(); i++) {
            T key = list.get(i);
            int j = i - 1;

            while (j >= 0 && order.compare(list.get(j), key) > 0) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }
}
