package com.rauaap.calories;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

/** A list adapter over one layout; subclasses fill in each row. */
abstract class ItemAdapter<T> extends BaseAdapter {
    private final int layout;
    private List<T> items = new ArrayList<>();

    ItemAdapter(int layout) {
        this.layout = layout;
    }

    void set(List<T> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    abstract void bind(View view, T item);

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public T getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView != null ? convertView
                : LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        bind(view, items.get(position));
        return view;
    }
}
