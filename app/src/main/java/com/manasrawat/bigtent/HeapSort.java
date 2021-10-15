package com.manasrawat.bigtent;

import java.util.List;

public class HeapSort {

    private int sort; //global sort

    //constructor
    public HeapSort(List<Item> data, int sort) {
        int n = data.size(); //number of data items to sort
        if (n > 1) { //check, to avoid unnecessarily executing code
            this.sort = sort; //set global variable value

            //recursively construct heap by iterating downwards from midpoint
            for (int i = n / 2 - 1; i >= 0; i--) heap(data, n, i);

            //move down heap tree, to reverse
            for (int i = n - 1; i >= 0; i--) {
                // Move current root to end
                Item temp = data.get(0); //initial item, temporarily store
                data.set(0, data.get(i)); //set to current item iterated
                data.set(i, temp); //set iterated item to what initial was (swap)

                heap(data, i, 0); //get max reduced heap
            }
        }
    }

    //traverse tree
    private void heap(List<Item> data, int n, int i) {
        int largest = i; //root
        int left = 2 * i + 1; //left child
        int right = 2 * i + 2; //right child

        // If left/right child is larger than largest so far
        for (int k = 0; k < 2; k++) { //iterate to check both left and right child
            int comparator = k == 0 ? left : right; //get respective one as per iteration

            if (comparator < n //if child in range of data being traversed through
                //if sorting by economic value, child's is greater than the current largest's
                && ((sort == 3 ? data.get(comparator).getEcon() > data.get(largest).getEcon()
                //if sorting by social value, child's is greater than the current largest's
                : (sort == 4 ? data.get(comparator).getSoc() > data.get(largest).getSoc()
                //if sorting is text-based, child is lexicographically greater than the current largest
                : data.get(comparator).getSorter(sort).toLowerCase()
                  .compareTo(data.get(largest).getSorter(sort).toLowerCase()) > 0))
                //if sorting by economic value, child's equals current largest's
                || (((sort == 3 && data.get(comparator).getEcon() == data.get(largest).getEcon())
                //if sorting by social value, child's equals current largest's
                || (sort == 4 && data.get(comparator).getSoc() == data.get(largest).getSoc()))
                //for fallback criteria: child's case 0 text sort (surname-first) is greater than largest's
                && data.get(comparator).getSorter(0).toLowerCase()
                   .compareTo(data.get(largest).getSorter(0).toLowerCase()) > 0)))
                largest = comparator; //new largest set if the conditions above are met
        }

        if (largest != i) {         //if not root
            Item swap = data.get(i); //set to iterated item, holding temporarily
            data.set(i, data.get(largest)); //set iterated as largest
            data.set(largest, swap); //set largest as the temporarily-held data (what iterated was)

            heap(data, n, largest); //recursively heap subsequent sub-tree
        }
    }
}

