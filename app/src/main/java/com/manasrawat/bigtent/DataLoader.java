package com.manasrawat.bigtent;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

import java.util.List;

//loads data from DataRetriever
public class DataLoader extends AsyncTaskLoader<List<Item>> {

    //Constructor, passes passed application context to parent
    public DataLoader(Context context) {
        super(context);
    }

    @Override
    protected void onStartLoading() {
        forceLoad();
    }

    @Override
    public List<Item> loadInBackground() {
        return DataRetriever.getData(); //load data from DropBox
    }
}

