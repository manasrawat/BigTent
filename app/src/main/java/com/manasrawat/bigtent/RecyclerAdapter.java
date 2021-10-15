package com.manasrawat.bigtent;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.*;

import static com.manasrawat.bigtent.Activity.*;

public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ItemHolder> {
    private List<Item> dataset; //Recycler View data
    private Context context; //interface to global information about app environment (passed from the activity)
    private ItemClickListener itemClickListener; //for class-global storage

    //Class constructor
    public RecyclerAdapter(Context context, List<Item> dataset) {
        //initialise global variables
        this.context = context;
        this.dataset = dataset;
    }

    //set each recycler view list item's layout
    @NonNull
    @Override
    public ItemHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        @SuppressLint("InflateParams") View layoutView = LayoutInflater.from(viewGroup.getContext())
                                                         .inflate(R.layout.item, null);
        return new ItemHolder(layoutView);
    }

    //set the content and functionality of each list item
    @Override
    public void onBindViewHolder(@NonNull ItemHolder itemHolder, int i) {
        int pos = itemHolder.getAdapterPosition(); //get item index
        Item currentItem = dataset.get(pos); //get item object from index
        String headingMain = currentItem.getHeadingMain(); //get item main heading (MP first name/policy name)
        double economic = currentItem.getEcon(), social = currentItem.getSoc(); //get item's economic and social values

        //when showing list of MPs
        if (mode == 0) {
            //selectable/clickable animation enabled
            TypedArray typedArray = context.obtainStyledAttributes(new int[]{ R.attr.selectableItemBackground });
            Drawable drawable = typedArray.getDrawable(0);
            typedArray.recycle();
            itemHolder.item.setForeground(drawable);

            //if not Sinn Fein (who never turn up to vote), set MP's economic and social positions to visually show;
            //else, for Sinn Fein, show that they don't vote
            itemHolder.detail2.setText(currentItem.getEcon() == 11.0 ? "DON'T VOTE" :
                    "(" + round(economic) + "," + round(social) + ")");
            itemHolder.detail2.setVisibility(View.VISIBLE); //show position
        } else {
            itemHolder.item.setForeground(null); //no on-select animation
            itemHolder.detail2.setVisibility(View.GONE); //not needed
        }

        //visually set the item's main title: if item is an MP, add extra heading (surname), else (when item is a
        // policy) don't
        itemHolder.heading.setText(
                headingMain + (currentItem.getHeadingExtra() != null ? " " + currentItem.getHeadingExtra() : ""));

        itemHolder.category.setText(currentItem.getCategory()); //visually set item category (party/policy)
        //if not on show-all-categories filter (i.e. are filtering by a specific category), hide the category
        //as redundant to show, given that all items will be of the same, filtered category
        itemHolder.category.setVisibility(selected == 0 ? View.VISIBLE : View.GONE);

        if (currentItem.getDetail() != null) { //MP constituency or MP's support + their party's support for a policy
            //in % (not null when mode = 0 or 1)
            itemHolder.detail1.setText(currentItem.getDetail()); //visually set
            itemHolder.detail1.setVisibility(View.VISIBLE); //make visible if it wasn't previously
        } else itemHolder.detail1.setVisibility(View.GONE); //not needed; not just hide, but prevent from taking any
        //space up as well

        if (mode == -1) { //if in quiz
            itemHolder.choice.setVisibility(View.VISIBLE); //show policy option selections - only if in the quiz
            //Establish the default (Please select) + the answer options
            String[] toAdd = {
                    "Please select", "Strongly agree", "Agree", "Neutral/Unsure", "Disagree", "Strongly disagree"
            };
            //establish option selection functionality
            //create array adapter from the toAdd array
            ArrayAdapter<String> sortAdapter = new ArrayAdapter<String>(context, R.layout.sort_spinner_layout,
                    new ArrayList<>(Arrays.asList(toAdd))) {
                @Override
                public boolean isEnabled(int position) {
                    return position != 0; //don't count default as an option; disable it
                }

                //establish selection menu layout
                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) view; //get options item
                    //if default, option being disabled is reflected through text colour
                    if (position == 0) tv.setTextColor(Color.GRAY);
                    else tv.setTextColor(Color.BLACK);
                    return view;
                }
            };
            itemHolder.choice.setAdapter(sortAdapter); //bind adapter to view

            if (quizChoices[0].containsKey(headingMain)) itemHolder.choice.setSelection(quizChoices[0].get(headingMain));

            final boolean[] isTouched = {false};
            //if touched, option is selected; boolean to tell whether this has occurred, for selection listener
            itemHolder.choice.setOnTouchListener((v, event) -> {
                isTouched[0] = true;
                return false;
            });

            //what to do on selection of an option
            itemHolder.choice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    //if policy question not been answered before, add it and the position of the option/answer
                    //chosen to the first hash map
                    //check if policy question being answered, and if the selected answer
                    // doesn't match the previously selected one, to avoid executing the code unnecessarily
                    if (isTouched[0] &&
                       (!quizChoices[0].containsKey(headingMain) || quizChoices[0].get(headingMain) != position)) {
                        quizChoices[0].put(headingMain, position);
                        //each value in the array goes from strongly agree (10) to strongly disagree (-10)
                        int[] weight = {10, 5, 0, -5, -10};
                        //start at 1 to not count the unselected default, and iterate through the rest of
                        //the user-selectable options
                        for (int i = 1; i < sortAdapter.getCount(); i++) {
                            if (position == i) { //if iterator matches selected position
                                //check policy for both social and economic value as some policies are both
                                //if it has an economic/social value, put that multiplied by the selected option weight
                                //(1 subtracted from iterator to, again, not count the default non-selection)
                                //into the respective hash map
                                if (economic != 0.0) quizChoices[1].put(headingMain, (int) economic * weight[i - 1]);
                                if (social != 0.0) quizChoices[2].put(headingMain, (int) social * weight[i - 1]);
                                break; //have found match to position, so no need to iterate through the rest
                            }
                        }
                        isTouched[0] = false; //relieve selection
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }

            });
        } else itemHolder.choice.setVisibility(View.GONE); //if not in quiz, hide policy agreement selection options
    }

    @Override
    public int getItemCount() {
        return dataset.size();
    }

    public void clear() {
        //if not already empty, make it so, and then notify the recycler view to update visually
        if (!dataset.isEmpty()) {
            dataset.clear();
            notifyDataSetChanged();
        }
    }

    //add data items in bulk, and notify the recycler view
    public void addAll(List<Item> data) {
        dataset.addAll(data);
        notifyDataSetChanged();
    }

    //catches click events
    void setClickListener(ItemClickListener itemClickListener) {
        this.itemClickListener = itemClickListener;
    }

    //implemented by parent activity to respond to click events
    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }

    //object holder for each data item of the recycler view
    public class ItemHolder extends RecyclerView.ViewHolder implements View.OnClickListener,
            AdapterView.OnItemSelectedListener {

        public RelativeLayout item; //base layout
        public TextView heading, category, detail1, detail2;
        public Spinner choice; //for quiz only

        public ItemHolder(View item) {
            super(item);
            item.setTag(this); //bind item
            item.setOnClickListener(this); //set on-click functionality to that method implemented by this class
            //set global variables to their XML counterparts
            this.item = item.findViewById(R.id.item);
            heading = item.findViewById(R.id.heading);
            category = item.findViewById(R.id.category);
            detail1 = item.findViewById(R.id.detail1);
            detail2 = item.findViewById(R.id.detail2);
            choice = item.findViewById(R.id.choice);
        }

        @Override
        public void onClick(View view) {
            //set click for defining in activity
            if (itemClickListener != null) itemClickListener.onItemClick(view, getAdapterPosition());
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { }

        @Override
        public void onNothingSelected(AdapterView<?> parent) { }
    }

}
