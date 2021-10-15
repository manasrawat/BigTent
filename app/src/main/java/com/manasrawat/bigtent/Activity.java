package com.manasrawat.bigtent;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

public class Activity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<List<Item>>, SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemSelectedListener, RecyclerAdapter.ItemClickListener {

    private LoaderManager loaderManager; //manages loading and reloading of web-retrieved content from DataRetrieval
    private Context context; //interface enabling access to app-specific resources

    private boolean permsToLoad; //used to ensure unauthorised data (re)loading doesn't occur

    /*Declarations (and in some cases, initialisations) of global variables*/

    //Toolbar views declarations
    private Spinner spinner; //used to sort the list of data presented in the RecyclerView
    private TextView title; //if on quiz or listing MPs, not visible; name of MP if showing one's voting record
    private Button button1, quizButton, resetButton; //used to go to quiz
    // (or graphically display quiz result if already on quiz)
    // and to display MP's political position alongside user's quiz derived one and MP's party's one, also graphically

    //Other main activity views declared
    private SwipeRefreshLayout refresher; //Used to refresh the recyclerView and its data by pulling down
    private View loadingIndicator; //loading circle, displayed when app started up and moving between different modes
    // (and thus datasets)
    private Snackbar snack = null; //used to make internet connection error message if no content loaded

    //Graph views
    private View graphLayout, closestMPsLayout; //the UI for the
    private RelativeLayout coordinates; //List of TextView displaying the (economic, social) positions of the MP,
    // their party and the User. MP and Party ones only shown when on an individual MP's page.
    // User one shown on both that and the quiz
    private GraphView graphView; //the user interface for the political compass graph

    private LinearLayout closestMPsBase; //base layout for dialog showing politically-closest MPs to user
    private LinearLayout[] closestMPsSections = new LinearLayout[2]; //one for economically closest, other for socially

    //Accessories
    public static SharedPreferences sharedPreferences; //used to store and save data even after app closed
    public static List<String> categories = new ArrayList<>(); //to be accumulated with the distinct parties
    // represented in parliament
    private LinearLayoutManager layoutManager; //manages recyclerView
    private Menu optionsMenu; //shows different options to filter the current data
    private RecyclerAdapter adapter; //adapts the recyclerView
    private ArrayAdapter<String> sortAdapter; //for the filter options menu
    private List<Item> loadedData = new ArrayList<>(), //data loaded from DropBox for use in mode,
            displayData = new ArrayList<>(); //data being displayed (depends on sort and filter)

    //Numerical
    public static int selected = 0, //filter option
            sortBy = 0, //sort option
            mode, //quiz = -1; list of MPs = 0; MP's voting record = 1
            black = android.R.color.black;
    private int gotoPos = 0, //position of data item that user clicked/was on when they changed mode
            accent = R.color.colorAccent;
    public static double partyEcon = Double.NaN, partySoc = Double.NaN; //socioeconomic position of the party
    //of the mode-1 MP whose page the user is on
    public static double memberEcon, memberSoc; //socioeconomic position the mode-1 MP whose page the user is on

    //0=item position,1=econ,2=soc; to be stored in SharedPreferences
    public static HashMap<String, Integer>[] quizChoices = new HashMap[3];
    private Stack<int[]> priorStates = new Stack(); //accumulates the filter, sort, data item index and mode
    // the user was last on prior to mode changes; so that they can be sequentially restored via back button press
    private HashMap<Integer, int[]> values; //dictionary of ASCII characters (key) and their corresponding 7-bit
    // binary values

    //Misc
    public static String url, party, code; //url=ID of current mode-1 MP; party = their party;
    // code is to access the Dropbox folder hosting the JSON data needed for all of the different modes
    public static ObjectMapper objectMapper = new ObjectMapper(); //
    // Jackson JSON API class used to read, write, serialise and traverse JSON data
    private AlertDialog dialog; //for displaying closest MPs to user politically (mode 0)
    // or MP's political positions (mode 1)
    private List<String> sortOptions = new ArrayList<>(Arrays.asList("Sort by surname",
            "Sort by first name", "Sort by constituency",
            "Sort by economic", "Sort by social", "Sort by party"));

    private boolean showClosestWithoutPrompt = false; //on whether to show closest MPs after quiz completion

    private Toast toaster; //stores latest toast, to dismiss it later if needs be

    private LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT); //height and width for dynamically generated views

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); //set XML UI
        context = getApplicationContext();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        establishViews();
        createASCIIBinaries();

        if (firstTime()) { //app first run
            //code's value
            code = "CODE GOES HERE";
            mode = -1; //quiz mode
        } else {
            String key = sharedPreferences.getString("key", ""), //get the key
                    cipher = sharedPreferences.getString("cipher", ""); //get the encrypted code
            code = vernamCipher(cipher, key); //decode the code using the key
            mode = 0; //list of MPs
        }
        //generate new key and encrypted code (ciphertext), and save to access next time app opened
        String key = newKey(),
                cipher = vernamCipher(code, key);
        sharedPreferences.edit().putString("key", key).putString("cipher", cipher).apply();

        new DataRetriever(); //connect to DropBox
        loaderManager = getSupportLoaderManager();
        loadNewContent();
    }

    private boolean firstTime() {
        return sharedPreferences.getBoolean("firstTime", true);
    }

    private HashMap<String, Integer> savedChoices(int i) {
        try {
            //retrieve the respective dictionaries' values from their JSON-saved string form in sharedPreferences
            // - if they exist
            return objectMapper.readValue(sharedPreferences.getString("pos" + i, ""),
                    HashMap.class);
        } catch (JsonProcessingException e) { //mapping or processing error with object mapper
            Log.i("Error retrieving hash " + i, e.getMessage());
            return new HashMap<>();
        }
    }

    //checks if connected to a network (WiFi/Mobile Data), and if that network has a working internet connection
    // (so networks requiring a web sign-in that aren't signed-in-to aren't falsely equated to an internet connection)
    private boolean internetConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);

        //true if a network is connected to and it is validated (web-signed-in if needs be)
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private AlertDialog.Builder graphDialogBuilder, closestMPsDialogBuilder;
    private ActionBar actionBar;
    private ScrollView closestMPsScroll;

    //Initialise views (UI elements) and their adapters
    private void establishViews() {
        Toolbar toolbar = findViewById(R.id.toolbar); //XML initialisation of toolbar
        setSupportActionBar(toolbar); //set as app's main toolbar
        actionBar = getSupportActionBar();
        actionBar.setTitle(null); //remove default title to enable title TextView and spinner to take the place
        toolbar.setNavigationOnClickListener(this::goBack); //set back button functionality

        params.setMargins(0, 5, 0, 0);

        title = findViewById(R.id.title);
        graphLayout = getLayoutInflater().inflate(R.layout.graph, null); //set to the XML file
        coordinates = graphLayout.findViewById(R.id.pinpoint); //value is a view from the secondary layout

        closestMPsLayout = getLayoutInflater().inflate(R.layout.closest, null); //similarly set to an XML file
        closestMPsBase = closestMPsLayout.findViewById(R.id.base); //again, value = view from a secondary layout
        closestMPsScroll = closestMPsLayout.findViewById(R.id.closestScroll); //scroll view for closest MPs list,
        //which becomes necessary in case of too many MPs being equally economically/socially close to the user

        //set value of child linear layouts to hold the economically and socially closest MPs.
        // 2 multiplied by the iterator + 1 used to skip non-linear layout child views
        for (int i = 0; i < 2; i++) closestMPsSections[i] = (LinearLayout) closestMPsBase.getChildAt(2 * (i + 1));

        //initialise and set core properties of both dialog builders
        graphDialogBuilder = new AlertDialog.Builder(Activity.this).setView(graphLayout);
        closestMPsDialogBuilder = new AlertDialog.Builder(this).setView(closestMPsLayout)
                .setPositiveButton("OK", null);

        loadingIndicator = findViewById(R.id.loading_indicator);

        //Spinners and buttons
        spinner = findViewById(R.id.sortBySpinner);

        button1 = findViewById(R.id.button1);
        button1.setOnClickListener(this::button1Click); //set on click functionality, which varies between the modes

        quizButton = findViewById(R.id.quizButton);
        quizButton.setOnClickListener(this::quizButtonClick); //likewise, click functionality set

        resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(this::resetButtonClick);

        //RecyclerView
        //used to display the current mode's data; recyclerView status enables the
        //recycler view item holders to be recycled, thus saving space and making the processing faster
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true); //prevents scroll disruption
        layoutManager = new LinearLayoutManager(this); //manages RecyclerView layout
        recyclerView.setLayoutManager(layoutManager);
        adapter = new RecyclerAdapter(this, new ArrayList<>()); //manages RecyclerView list items
        recyclerView.setAdapter(adapter);

        //spinner
        sortAdapter = new ArrayAdapter<>(this, R.layout.sort_spinner_layout, sortOptions);
        spinner.setAdapter(sortAdapter); //manages Spinner items
        spinner.setOnItemSelectedListener(this); //activity implements spinner selection listener already

        //refresher
        refresher = findViewById(R.id.refresh);
        refresher.setOnRefreshListener(this); //activity implements refresh listener already

        //graph
        graphView = graphLayout.findViewById(R.id.gv); //initialise as per XML
        drawGraph();
    }

    //Set and define properties of application toolbar
    //set on-click functionality of the toolbar's back button
    private void goBack(View view) {
        int[] prior = priorStates.pop(); //access and remove from stack the last-in item

        mode = prior[0]; //restore mode value to prior mode
        sortBy = prior[1]; //restore sort mode to prior one
        selected = prior[2]; //restore filter to prior one
        gotoPos = prior[3]; //to go to last position user was on when they had exited the mode they're now returning to

        loadNewContent(); //to prepare app for the brief intermission
        // before a new mode and its respective dataset is loaded
    }

    private void drawGraph() {
        graphView.bringToFront(); //so backdrop doesn't block the graph

        //set X-axis range
        graphView.getViewport().setXAxisBoundsManual(true); //fix size
        //cap at limits
        graphView.getViewport().setMinX(-10);
        graphView.getViewport().setMaxX(10);

        //set Y-axis range
        graphView.getViewport().setYAxisBoundsManual(true); //fix size
        //cap at limits
        graphView.getViewport().setMinY(-10);
        graphView.getViewport().setMaxY(10);

        //set the X and Y spacing (21 as also accounting for the origin)
        graphView.getGridLabelRenderer().setNumHorizontalLabels(21);
        graphView.getGridLabelRenderer().setNumVerticalLabels(21);

        //hide labelling to maintain the principles of the Political Compass
        graphView.getGridLabelRenderer().setVerticalLabelsVisible(false);
        graphView.getGridLabelRenderer().setHorizontalLabelsVisible(false);

        //transparent in order to enable the backdrop 4-colour, 4-quadrant compass background
        graphView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
    }

    private void createASCIIBinaries() {
        values = new HashMap<>();
        //set binary values of the first 2 ASCII values (starting with only 0000000 causes the algorithm to fail)
        values.put(0, new int[]{0, 0, 0, 0, 0, 0, 0});
        values.put(1, new int[]{0, 0, 0, 0, 0, 0, 1});

        for (int DEC = 2; DEC < 128; DEC++) { //iterate through remaining ASCII characters
            int increment = 1, i = 6, carryover = 0;
            //increment = how much prior ASCII value to be increased by (1 binary valuation)
            //i=6=index of last bit of 7-binary-digit number (for later backwards-iteration)
            int[] prev = values.get(DEC - 1); //get binary value of previous (1-less) character
            assert prev != null;
            int prevInt = 0, last = prev.length - 1;
            for (int j = last; j >= 0; j--) { //start at the last bit, iterating up the digits
                int bit = prev[j]; //get current bit
                if (bit == 1) {
                    for (int k = 0; k < last - j; k++) bit *= 10; //turn bit into multiple of 10 as per  digit location
                    prevInt += bit; //add to overall bit
                }
            }

            int[] binary = new int[7];
            for (; prevInt != 0; prevInt /= 10) { //iterate (in backwards) with a step of diving the integer by 10
                // each time
                //set binary digit
                int val = prevInt % 10 + increment % 10 + carryover;
                binary[i--] = val % 2;
                carryover = val / 2; //for 1+1 and 1+1+1 addition
                increment /= 10; //move along binary digits
            }
            if (carryover != 0) binary[i--] = carryover; //carrying over to first digit
            values.put(DEC, binary); //add character's binary value to dictionary
        }
    }

    //create new randomised key
    private String newKey() {
        Random random = new Random();
        StringBuilder key = new StringBuilder();
        //iterate code to build up randomised key of same length
        //draws from same pool of indexes (0 to 127) corresponding to all the ASCII characters
        for (int i = 0; i < code.length(); i++) key.append((char) random.nextInt(128));
        return key.toString();
    }

    private String vernamCipher(String code, String key) {
        StringBuilder result = new StringBuilder();

        //to deal with problem whereby the code or key's last character being a space resulting in 4 additional
        // space characters
        if (code.length() > key.length()) code = code.substring(0, key.length());
        else if (key.length() > code.length()) key = key.substring(0, code.length());

        //iterate through code's characters (alongside key's)
        for (int i = 0; i < code.length(); i++) {
            int[] codeChar = values.get((int) code.charAt(i)), //get binary value of i-th character of code
                    keyChar = values.get((int) key.charAt(i)), //get binary value of i-th character of key
                    cipherChar = new int[7]; //array to hold the binary value of the resultant cipher's i-th character
            assert codeChar != null;
            for (int j = 0; j < codeChar.length; j++) { //iterate through code's i-th character's binary value's
                // 7 digits
                assert keyChar != null;
                //set cipher digit to 1 if XOR conditions met; else 0
                if (codeChar[j] == 1 && keyChar[j] == 0 || codeChar[j] == 0 && keyChar[j] == 1) cipherChar[j] = 1;
                else cipherChar[j] = 0;
            }

            //iterate through ASCII character dictionary
            for (HashMap.Entry<Integer, int[]> entry : values.entrySet()) {
                //find the cipher's i-th ASCII character from its corresponding binary value
                if (Arrays.equals(cipherChar, entry.getValue())) {
                    int oneChar = entry.getKey(); //get the decimal value
                    result.append((char) oneChar); //add to StringBuilder after converting from decimal to char form
                    break; //halt to avoid unnecessary further iterations when objective achieved
                }
            }
        }
        return result.toString();
    }

    @Override
    public android.support.v4.content.Loader<List<Item>> onCreateLoader(int id, Bundle args) {
        return new DataLoader(this); //set loader functionality
    }

    //on completion of loader
    @Override
    public void onLoadFinished(@NonNull android.support.v4.content.Loader<List<Item>> loader, List<Item> data) {
        if (permsToLoad) { //if authorised
            permsToLoad = false; //revoke
            if (!refresher.isEnabled()) refresher.setEnabled(true);
            refresher.setRefreshing(false); //stop the refresher
            setButtonsState(true); //restore the buttons' functions
            loadingIndicator.setVisibility(View.GONE); //indicates that loading done

            loadedData = data; //set new data
            if (!loadedData.isEmpty()) { //ensure there's content to worth with
                populateFilter(); //create filters
                filterDisplayedData(); //filter data
                updateAdapter(); //restore prior filter modes and RecyclerView index position, if recorded
                graphView.removeAllSeries(); //remove any old coordinates that were on the graph
                set(optionsMenu.getItem(selected), accent); //highlight current filter
                if (mode == 0) showClosestMPs();
                else makeGraph();
            } else if (!internetConnected()) noWiFi();
            else makeToast("Could not load data");
        }
    }

    //filter data
    private void filterDisplayedData() {
        //if showing all data, regardless of category
        if (!displayData.isEmpty()) displayData.clear();
        if (selected == 0) displayData.addAll(loadedData);
            //if showing specific category's data,
            //for each item in the dataset, add to category data list if it comes under that category
        else for (Item item : loadedData)
            if (categories.get(selected - 1).equals(item.getCategory())) displayData.add(item);
    }

    //called when a previously created loader is being reset, and thus making its data unavailable.
    @Override
    public void onLoaderReset(@NonNull android.support.v4.content.Loader<List<Item>> loader) {
        adapter.clear();
        Log.i("RESET", "Loader reset");
    }

    //when new data loaded, to find, visually construct and show the closest MPs to the user
    private void showClosestMPs() {
        //Show user's position, for comparision
        ((TextView) closestMPsBase.getChildAt(0)).setText("Most similar to you (" +
                round(toDouble("economic")) + "," + round(toDouble("social")) + ")");
        findClosestMPs();
        //reset layout of previous configurations
        if (closestMPsLayout.getParent() != null)
            ((ViewGroup) closestMPsLayout.getParent()).removeView(closestMPsLayout);
        dialog = closestMPsDialogBuilder.create(); //define dialog's current use as being to show the closest MPs
        if (showClosestWithoutPrompt) { //when exiting the quiz into mode 0 (list of MPs)
            showClosestWithoutPrompt = false; //so this isn't repeated anytime mode 0 is loaded
            dialog.show();
        }
    }

    private List<Item>[] closestSetsOfMPs;

    //traverses through data
    private void findClosestMPs() {
        closestMPsScroll.scrollTo(0, 0);
        closestSetsOfMPs = (ArrayList<Item>[]) new ArrayList[2]; //0=economically, 1=socially
        double[] userVals = {toDouble("economic"), toDouble("social")}; //get user' position into an array

        for (int i = 0; i < 2; i++) { //for both the economic and social lists
            List<Item> closestMPs = new ArrayList<>();
            closestMPs.add(displayData.get(0)); //add the first MP
            closestSetsOfMPs[i] = closestMPs; //set the value of the economic/social list
        }

        for (int i = 1; i < displayData.size(); i++) { //iterate through the rest of the MPs
            Item member = displayData.get(i); //get the current MP
            if (!member.getCategory().equals("Sinn Féin")) { //ensure they aren't abstentionist
                for (int j = 0; j < closestSetsOfMPs.length; j++) { //look from an economic lens first,
                    // then from a social one
                    List<Item> closestOne = closestSetsOfMPs[j]; //get list

                    //find the difference between the user's position and the MP's,
                    //and the small difference between the user's position and MP(s) iterated past so far
                    //ternary operator used in accordance with iterator to ensure the difference is found with the right
                    //property (economic value if iterator j = 0, else social value)
                    double diff = userVals[j] - (j == 0 ? member.getEcon() : member.getSoc()),
                            smallestDiff = userVals[j] - (j == 0 ? closestOne.get(0).getEcon() :
                                    closestOne.get(0).getSoc());
                    if (diff < 0) diff *= -1; //get difference as modulus/absolute value
                    if (smallestDiff < 0) smallestDiff *= -1; //likewise
                    if (diff <= smallestDiff) { //if MP is closer to the user's position than
                        // the previously closest MP(s) or is as close, add them to the economic/social list
                        if (diff < smallestDiff) closestOne.clear(); //remove all previously-closest MPs if newly
                        //closest MP is closer than them
                        closestOne.add(member); //add closest MP
                    }
                }
            }
        }
        for (int i = 0; i < 2; i++) populateDialogOfClosestMPs(i); //for loop for both economic and social sets
    }

    //0=econ;1=soc; visually updates
    private void populateDialogOfClosestMPs(int i) {
        closestMPsSections[i].removeAllViews(); //clear
        for (Item MP : closestSetsOfMPs[i]) { //iterate through closest MPs
            TextView MPView = new TextView(this);

            //MP first name + MP last name + MP economic position + MP social position set as text
            MPView.setText(MP.getHeadingMain() + " " + MP.getHeadingExtra() +
                    " (" + round(MP.getEcon()) + "," + round(MP.getSoc()) + ")");

            //make text bold, and make it change colour when pressed and clicked
            MPView.setTypeface(Typeface.DEFAULT_BOLD);
            MPView.setTextColor(getResources().getColorStateList(R.color.changing));

            MPView.setOnClickListener(v -> { //on click
                int position;
                if (sortBy == 3 || sortBy == 4) { //if sorting economically or socially
                    //find position of MP by binary search
                    position = search(MP, 0, displayData.size() - 1, 0);

                    //if the unique constituencies (details) don't match with actual MP at that position, this means
                    //that there are multiple MPs with the same economic/social value
                    if (!displayData.get(position).getDetail().equals(MP.getDetail())) {
                        //look below the found MP in the list
                        int findingPosDown = traverse(position, MP, true);
                        //if not found below, look above
                        if (findingPosDown == -1) position = traverse(position, MP, false);
                        else position = findingPosDown; //else if found below, set as such
                    }
                } else position = search(MP, 0, displayData.size() - 1, 1); //if sorting by string value
                //i.e. by first name/last name/party/seat (all of which are multi-criteria, except by unique-seat/
                // constituency)
                //check if not already in position
                if (layoutManager.findFirstCompletelyVisibleItemPosition() != position)
                    layoutManager.scrollToPositionWithOffset(position, 0); //scroll to position
            });
            MPView.setLayoutParams(params); //add margins, height and width to text view
            closestMPsSections[i].addView(MPView); //add text view to the economic/social layout
        }
    }

    //Binary Search
    private int search(Item MPToFind, int first, int last, int type) {
        if (last >= first) { //ensure in range
            int centre = first + (last - first) / 2; //get middle value
            if (type == 0) { //numerical binary search
                //get relevant property of currently-in-the-middle-MP and the MP to find
                double toFind = sortBy == 3 ? MPToFind.getEcon() : MPToFind.getSoc();
                double comparator = sortBy == 3 ? displayData.get(centre).getEcon() : displayData.get(centre).getSoc();

                if (toFind == comparator) return centre; //found MP
                    //MP above centre; perform recursion in upper half of section already searched through
                else if (toFind > comparator) return search(MPToFind, centre + 1, last, type);
                    //MP below centre; perform recursion in lower half of section already searched through
                else if (toFind < comparator) return search(MPToFind, first, centre - 1, type);
            } else { //string-based binary search
                //same principles as numerical one
                String toFind = MPToFind.getSorter(sortBy).toLowerCase();
                String comparator = displayData.get(centre).getSorter(sortBy).toLowerCase();

                if (toFind.equals(comparator)) return centre;
                else if (toFind.compareTo(comparator) > 0) return search(MPToFind, centre + 1, last, type);
                else if (toFind.compareTo(comparator) < 0) return search(MPToFind, first, centre - 1, type);
            }
        }
        return -1;
    }

    //to find the MP if there are multiple MPs with same value as them
    //as the BinarySearch was done on sorted data, the MPs of the same value are consecutively located
    //so traversal need only be immediately either side of the found-but-not-wanted MP
    private int traverse(int j, Item MPToFind, boolean moveBelow) {
        double comparator = sortBy == 3 ? MPToFind.getEcon() : MPToFind.getSoc(); //relevant property
        while (moveBelow ? j >= 0 : j < displayData.size()) { //respective conditions for whether traversing below
            //or above the first-instance-MP
            Item currentMP = displayData.get(j); //get the currently-iterated MP (current MP)
            //if value matches the current MP's
            if ((sortBy == 3 ? currentMP.getEcon() : currentMP.getSoc()) == comparator) {
                //if unique constituencies of the current MP and the MP to find match, the index position has been found
                if (currentMP.getDetail().equals(MPToFind.getDetail())) return j;
            } else break; //means MP to find not above/below first instance MP, but below/above respectively
            //iterator changed according to whether the traversal is below or above the first instance MP
            if (moveBelow) j--;
            else j++;
        }
        return -1; //not found
    }

    //convert into double from saved long state
    private double toDouble(String id) {
        return Double.longBitsToDouble(sharedPreferences.getLong(id, Double.doubleToLongBits(0.0)));
    }

    //round the double to 3 significant figures
    public static String round(double val) {
        return new BigDecimal(val).round(new MathContext(3))
                .stripTrailingZeros().toPlainString();
    }

    //when refreshing
    @Override
    public void onRefresh() {
        setButtonsState(false);
        if (internetConnected()) {
            refresher.setRefreshing(true); //refresh throbber visible and circling
            permsToLoad = true; //authorise load
            loaderManager.restartLoader(0, null, this);
            Log.i("Refresh", "Refreshed");
        } else {
            refresher.setRefreshing(false); //stop the refresher
            setButtonsState(true);
            noWiFi();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        optionsMenu = menu; //global variable defined
        getMenuInflater().inflate(R.menu.menu, menu); //XML layout attached
        return super.onCreateOptionsMenu(menu);
    }

    //visually update data in UI
    private void updateAdapter() {
        if (adapter.getItemCount() != 0) adapter.clear(); //clear recycler view
        //check if position in data range, in case of MP resignation update on the DropBox-hosted end
        layoutManager.scrollToPositionWithOffset(
                gotoPos < displayData.size() ? gotoPos : displayData.size() - 1, 0);
        gotoPos = 0; //reset
        adapter.addAll(displayData); //visually updated
    }

    //fill options menu with categories
    private void populateFilter() {
        List<Item> localData = new ArrayList<>(loadedData);
        optionsMenu.clear(); //reset
        optionsMenu.add(0, Menu.FIRST, Menu.NONE, "Show all"); //first item, default; shows items of
        //all categories

        if (mode != 1 && sortBy != 5) new HeapSort(localData, 5); //categorically sort the dataset
        //sortMisc(toFilter); //move misc data to end
        if (!categories.isEmpty()) categories.clear(); //reset categories list
        int counter = 0; //for assigning ID to categories

        for (Item member : localData) { //iterate through dataset
            String category = member.getCategory();
            if (!categories.contains(category)) { //if not included in category list already
                categories.add(category);
                counter++; //increment category ID counter
                optionsMenu.add(0, Menu.FIRST + counter, Menu.NONE, category); //add category to filter
                // menu
            }
        }
    }

    //when filtering
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int pos = item.getItemId() - 1; //subtraction so it doesn't count show all
        if (pos != selected) { //ensure the current filter mode isn't being clicked, to avoid unnecessarily filtering
            set(optionsMenu.getItem(selected), black); //unselect previous filter text
            selected = pos; //universally set new filter mode
            set(optionsMenu.getItem(selected), accent); //select newly-selected filter text
            if (loadedData.size() != 1) filterDisplayedData(); //filter data (no need to if only 1 data item)
            updateAdapter(); //filter dataset itself
            if (mode == 0) { //when listing MPs
                findClosestMPs();
                //if filter mode not to show all (i.e. user is filtering by party), remove the party sort from
                //the sorter. if filter mode changed to show all, restore party sort
                if (sortAdapter.getCount() == 6 && selected != 0) sortAdapter.remove("Sort by party");
                else if (sortAdapter.getCount() == 5 && selected == 0) sortAdapter.add("Sort by party");
            } else if (mode == -1) { //quiz
                if (selected == 0) {
                    button1.setText("CLEAR ALL"); //to clear all policies' options
                    resetButton.setText("RESET ALL");
                } else {
                    button1.setText("CLEAR"); //to clear the options of the policies of the shown topic
                    resetButton.setText("RESET");
                }
            }
        }
        return super.onOptionsItemSelected(item);
    }

    //to set text colour of an options menu item
    private void set(MenuItem item, int color) {
        SpannableString ss = new SpannableString(item.getTitle().toString()); //get text in markup-compatible form
        //reflect colour onto spannable string
        ss.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, color)), 0, ss.length(), 0);
        item.setTitle(ss); //change text colour of item itself by setting the label to it
    }

    //when sorting (mode 0)
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (sortBy != position) { //don't execute code unnecessarily if already on selected sort
            sortBy = position; //universally set new sort mode
            new HeapSort(loadedData, sortBy); //sort
            if (displayData.size() > 1) { //ensure it's actually worthwhile to sort (1 data item needs no sorting)
                filterDisplayedData();
                updateAdapter(); //get sorted data

                //to sort list of closest MPs
                for (int i = 0; i < 2; i++) { //first economically, then socially
                    //no point resorting if only one MP (already accounted for in HeapSort code too)
                    new HeapSort(closestSetsOfMPs[i], sortBy); //sort list of economically/socially closest MPs
                    if (closestSetsOfMPs[i].size() != 1) populateDialogOfClosestMPs(i); //reflect in dialog UI
                }
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    //when clicking on item to launch selected MP's record
    @Override
    public void onItemClick(View view, int position) {

        if (!refresher.isRefreshing()) { //to prevent app from crashing

            if (displayData.get(position).isActive()) { //ensure MP has voted
                //push current configurations to stack
                priorStates.push(new int[]{mode, sortBy, selected, position});
                mode = 1;
                sortBy = 5;
                selected = 0;
                gotoPos = 0;

                //set toolbar title to MP's name
                title.setText(
                        displayData.get(position).getHeadingMain() + " " + displayData.get(position).getHeadingExtra());
                //set cross-class party and ID of MP
                party = displayData.get(position).getCategory();
                url = displayData.get(position).getUrl();

                loadNewContent(); //prepare for new data to be loaded into activity
            } else makeToast("Haven't voted");

        }
    }

    //prep for the intermission before the new data is loaded
    private void loadNewContent() {
        permsToLoad = true; //authorise load
        if (!displayData.isEmpty()) displayData.clear(); //to prevent app crash from changing sort during load

        if (loaderManager.hasRunningLoaders()) loaderManager.destroyLoader(0); //if another mode is being loaded,
        // stop it to prevent the loader from collapsing in on itself

        if (refresher.isRefreshing()) refresher.setRefreshing(false); //prevent data from being loaded twice when the
        //load completes, which would lead to the recyclerView being populated with the dataset twice
        refresher.setEnabled(false); //also to prevent the loader from collapsing in on itself
        setButtonsState(false);
        //hide dialog box's User/Party/MP position TextViews via iteration
        for (int i = 0; i < coordinates.getChildCount(); i++)
            coordinates.getChildAt(i).setVisibility(View.GONE);

        //bar title only needed to show the name of the MP whose record is on display
        title.setVisibility(mode == 1 ? View.VISIBLE : View.GONE);

        //clear and reset the following views to avoid the previous mode's configurations from being shown
        // during the brief loading-transition period to the new mode (also to prevent errors via clicking)
        if (optionsMenu != null) {
            optionsMenu.clear();
            optionsMenu.add(0, Menu.FIRST, Menu.NONE, "");
            optionsMenu.getItem(0).setEnabled(false); //can't be clicked
        }

        loadingIndicator.setVisibility(View.VISIBLE); //show the data is being loaded
        adapter.clear(); //clear current data

        if (mode == -1) { //quiz
            button1.setText("CLEAR ALL");
            quizButton.setText("FIND");
            resetButton.setVisibility(View.VISIBLE);

            //if (!appPaused) {
            for (int i = 0; i < 3; i++) {
                quizChoices[i] = new HashMap<>(); //reset displayed choices data
                //if quiz done before
                if (sharedPreferences.contains("pos" + i)) quizChoices[i].putAll(savedChoices(i));
            }
            //}
        } else { //list of MPs and MP page
            button1.setText("•");
            quizButton.setText("QUIZ");
            resetButton.setVisibility(View.GONE);
        }

        //when on mode 0, or app opened for first time
        if (priorStates.isEmpty()) {
            //hide back button navigation
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setDisplayShowHomeEnabled(false);
        } else {
            //show back navigation
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        if (mode == 0) { //list of MPs
            //restore necessary functions
            adapter.setClickListener(this); //so clicking recycler view item launches MP's page
            spinner.setVisibility(View.VISIBLE); //restore sort
        } else {
            //disable recycler view items from being clicked to prevent error (only clickable on mode 0, to open
            // selected MP's record)
            adapter.setClickListener(null);
            spinner.setVisibility(View.GONE); //spinner only relevant in mode 0/MP list
        }
        loaderManager.restartLoader(0, null, this); //reload the data
    }

    //prevent interference or hindrance when data being (re)loaded
    private void setButtonsState(boolean state) {
        //to prevent the operation from being carried out before the required data is loaded,
        //which would lead to the wrong data being used or the app from crashing, disable button click function
        //and in case button in pressed state before, reset to prevent discrepancy with new status
        if (button1.isPressed()) button1.setPressed(false);
        button1.setClickable(state);
        if (quizButton.isPressed()) quizButton.setPressed(false);
        quizButton.setClickable(state);
        //button only relevant and shown in quiz mode
        if (mode == -1) {
            if (resetButton.isPressed()) resetButton.setPressed(false);
            resetButton.setClickable(state);
        }

        if (toaster != null) toaster.cancel(); //hide any toasts that may be visible
        if (snack != null && snack.isShown()) snack.dismiss(); //hide any snackbars that are visible
        if (dialog != null && dialog.isShowing()) dialog.dismiss(); //hide any open dialogs
    }

    //to plot (economic, social) points on graph
    private void constructCompass(int color, double econ, double soc) {
        //series holding the point value coordinate
        PointsGraphSeries<DataPoint> series = new PointsGraphSeries<>(new DataPoint[]{new DataPoint(econ, soc)});
        series.setColor(getResources().getColor(color)); //uses colour passed in as argument
        series.setSize(15); //coordinate point size
        graphView.addSeries(series); //added to graph
    }

    //create graph dialog
    private void makeGraph() {
        //get user's positions from sharedPreferences
        double userEcon = toDouble("economic"), userSoc = toDouble("social");
        //store labels, colours, and coordinates for the positions of the MP, their party and the user
        // (tho MP and party ones irrelevant in mode -1/quiz)
        String[] types = {"MP", "Party", "User"};
        int[] colours = {android.R.color.holo_red_dark, android.R.color.holo_orange_dark, black};
        double[][] points = {{memberEcon, memberSoc}, {partyEcon, partySoc}, {userEcon, userSoc}};

        //if on quiz (-1), not MP page (1), start with 2 so that only the User position is set and visible
        for (int i = mode == -1 ? 2 : 0; i < coordinates.getChildCount(); i++) {
            if (!Double.isNaN(points[i][0])) { //for mode 1 in case of speaker, independents and 1-MP parties
                TextView current = (TextView) coordinates.getChildAt(i); //get relevant field (MP/party/user)
                //set field coordinate point in text
                current.setText(types[i] + ": (" + round(points[i][0]) + "," + round(points[i][1]) + ")");
                //show field
                current.setVisibility(View.VISIBLE);
                //set field coordinate point in graph
                constructCompass(colours[i], points[i][0], points[i][1]);
            }
        }
        //prevent removeView error
        if (graphLayout.getParent() != null) ((ViewGroup) graphLayout.getParent()).removeView(graphLayout);
        //if on quiz, save quiz progress and exit; else (if on MP page), just dismiss dialog (null)
        graphDialogBuilder.setPositiveButton(mode == -1 ? "DONE" : "OK", mode == -1 ?
                (DialogInterface.OnClickListener) (dialog, which) -> saveAndEndQuiz() : null);
        //only show redo button (simply dismisses dialog) in quiz
        if (mode == -1) {
            graphDialogBuilder.setNeutralButton("CHANGE", null);
            graphDialogBuilder.setNegativeButton("SAVE", (dialog, which) -> saveQuiz());
        }
        dialog = graphDialogBuilder.create(); //create graph-UI dialog
    }

    private void button1Click(View view) {
        if (!loadedData.isEmpty()) { //if there's data to worth with
            if (mode == 0 && optionsMenu.getItem(selected).getTitle().toString().equals("Sinn Féin"))
                makeToast("N/A"); //Sinn Fein have never sworn in as MPs, so have never turned up to even not vote
            else if (mode == -1) clearOrResetQuiz(false); //if on quiz mode
            else {
                if (mode == 0) closestMPsScroll.scrollTo(0, 0); //go back to top of list
                dialog.show(); //rest of the time on MP list mode 0, and mode 1 specific MP page
            }
        } else if (!internetConnected()) noWiFi();
        else makeToast("Could not load data"); //other error, can be caused when dropbox-hosted json data is
        //being updated
    }

    private void resetButtonClick(View view) {
        if (!loadedData.isEmpty()) clearOrResetQuiz(true); //there's actually data to revert
        else if (!internetConnected()) noWiFi();
        else makeToast("Could not load data"); //other error, can be caused when DropBox-hosted json data is
        //being updated
    }

    double userEconPos, userSocPos; //user's position

    //to enter quiz or find quiz result
    private void quizButtonClick(View view) {
        if (mode == -1) { //if already on quiz (finding result)
            if (!loadedData.isEmpty()) { //if quiz data has loaded
                graphView.removeAllSeries(); //remove prior position

                userEconPos = 0;
                userSocPos = 0;

                //foreach loop finds total for economic and social questions answered
                for (double econPos : quizChoices[1].values()) userEconPos += econPos;
                for (double socPos : quizChoices[2].values()) userSocPos += socPos;

                //after making sure at least a question has been answered (to prevent 0/0 which is nullity),
                //divide by number of questions respectively answered to get average, which equals user position
                //between 0 and 10 (inclusive) both economically and socially
                if (!quizChoices[1].isEmpty()) userEconPos /= quizChoices[1].size();
                if (!quizChoices[2].isEmpty()) userSocPos /= quizChoices[2].size();

                //display the economic and social positions in text
                ((TextView) coordinates.getChildAt(2)).setText("(" + round(userEconPos) + "," + round(userSocPos) + ")");
                //graphically display said positions
                constructCompass(black, userEconPos, userSocPos);
                //show result of quiz
                dialog.show();
            } else if (!internetConnected()) noWiFi(); //no internet available
            else makeToast("Could not load data");
        } else { //if trying to launch quiz
            //index of recycler view item in focus (fully appears first below toolbar)
            int firstPos = layoutManager.findFirstCompletelyVisibleItemPosition();
            //put current configurations in the last mode stack
            priorStates.push(new int[]{mode, sortBy, selected, firstPos});

            //prepare for new mode with new configurations
            mode = -1;
            sortBy = 0;
            selected = 0;
            gotoPos = 0;
            loadNewContent();
        }
    }

    //to clear the questions answered in the quiz or to restore the quiz to its last saved state
    //if reset=true: restoring; else if false then clearing
    private void clearOrResetQuiz(boolean reset) {
        boolean adapterClear = false; //don't visually clear
        for (int i = 0; i < 3; i++) { //iterate through the 3 HashMaps (for question/policy position, economic
            //value and social value )
            //if restoring quiz to last saved state, if user has done the quiz and/or completed economic or social
            //questions, and the user has actually answered any of the questions with an option
            if (reset && sharedPreferences.contains("pos" + i) && !savedChoices(i).isEmpty()) {
                if (!quizChoices[i].equals(savedChoices(i))) { //if the displayed user=answered qui\ data
                    // differs from the saved one
                    if (selected == 0) { //when all policies on display
                        //clear all, if not already empty
                        if (!quizChoices[i].isEmpty()) quizChoices[i].clear();
                        //restore last-saved data to displayed data
                        quizChoices[i].putAll(savedChoices(i));
                    } else {
                        //iterate policies on display (of a specific category)
                        for (Item m : displayData) {
                            String policy = m.getHeadingMain(); //get policy to identify hash map items
                            //remove answered question if it hasn't been saved
                            //but if answered question has been saved but it isn't displayed as such in the current
                            //selection by the user, or if the user has answered it differently in the current
                            //unsaved display, add it visually
                            if (!savedChoices(i).containsKey(policy) && quizChoices[i].containsKey(policy))
                                quizChoices[i].remove(policy);
                            else if (savedChoices(i).containsKey(policy) && (!quizChoices[i].containsKey(policy)
                                    || !quizChoices[i].get(policy).equals(savedChoices(i).get(policy))))
                                quizChoices[i].put(policy, savedChoices(i).get(policy));
                        }
                    }
                    if (!adapterClear) adapterClear = true; //indicate that something has changed
                }
            } else {
                if (!quizChoices[i].isEmpty()) { //check if any of the quiz has actually been done
                    if (selected == 0) { //when all policies on display
                        //clear all and save as such to shared preferences
                        quizChoices[i].clear();
                    } else {
                        //iterate policies on display and remove them from the HashMap
                        for (Item m : displayData) quizChoices[i].remove(m.getHeadingMain());
                    }
                    if (!adapterClear) adapterClear = true; //indicate that something has changed
                } else if (i == 0) break; //if first hash map (holds positions of answered policies/questions)
                //is empty, then the rest are too, so no point iterating through them; break loop
            }
        }
        //only update the layout if something has been changed, else executing the code is redundant
        if (adapterClear) {
            adapter.clear(); //remove all items
            adapter.addAll(displayData); //add updated items dataset
        }
    }

    //save quiz
    private void saveQuiz() {
        //if first time app opened (and quiz being done), let the app know that the first time is done
        //so next time app opened, it opens with list of MPs (mode 0), not mode -1 quiz
        if (firstTime()) sharedPreferences.edit().putBoolean("firstTime", false).apply();

        //save position to app in bit form
        sharedPreferences.edit().putLong("economic", Double.doubleToRawLongBits(userEconPos))
                .putLong("social", Double.doubleToRawLongBits(userSocPos))
                .apply();

        //iterate through all 3 hash maps
        for (int i = 0; i < 3; i++) {
            //if quiz hasn't been done before (or questions that are specifically economic or social anyway)
            //or if the saved data doesn't match the unsaved data on display as done by the user
            if (!sharedPreferences.contains("pos" + i) || !quizChoices[i].equals(savedChoices(i))) {
                try {
                    //update saved data as the user-done HashMap
                    sharedPreferences.edit()
                            .putString("pos" + i, objectMapper.writeValueAsString(quizChoices[i]))
                            .apply();
                } catch (JsonProcessingException e) { //object mapper error
                    //write to app error has occurred
                    Log.i("Error setting hash " + i, e.getMessage());
                }
            }
        }
    }

    //when exiting quiz
    private void saveAndEndQuiz() {
        saveQuiz(); //auto-save progress
        if (priorStates.isEmpty()) { //when app first opened (user not having been on prior modes)
            mode = 0; //defaults to list of MPs
            selected = 0; //default filter
        } else {
            //retrieve values of last stack item
            int[] prev = priorStates.pop();
            mode = prev[0];
            sortBy = prev[1];
            selected = prev[2];
            gotoPos = prev[3];
        }

        if (mode == 0) showClosestWithoutPrompt = true; //automatically show user the closest MPs to them once
        //data in new mode has finished loading
        loadNewContent(); //prepare views and trigger mode change
    }

    //displays message for short period of time
    private void noWiFi() {
        if (loadedData.isEmpty()) { //if nothing to show user
            //snackbar error message that won't be disappear until dismissed by user clicking
            snack = Snackbar.make(findViewById(android.R.id.content),
                    "No internet connection", Snackbar.LENGTH_INDEFINITE)
                    .setAction("REFRESH", view -> onRefresh()) //user clicking refreshes page
                    .setActionTextColor(getResources().getColor(R.color.colorPrimary)); //text colour
            snack.show();
        } else makeToast("No internet connection"); //auto-disappears
    }

    //toast error message
    private void makeToast(String text) {
        toaster = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toaster.show();
    }
}