package com.manasrawat.bigtent;

import android.util.Log;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static com.manasrawat.bigtent.Activity.*;

public class DataRetriever {
    //global variables declarations
    private static DbxClientV2 client;

    //constructor, called by Activity when class instantiated
    public DataRetriever() {
        //to connect to the DropBox folder
        DbxRequestConfig config = DbxRequestConfig.newBuilder("JSONBigTent/2.0")
                .withUserLocale(Locale.getDefault().toString()).build();
        client = new DbxClientV2(config, code); //connect to DropBox
    }

    //retrieves data to fill RecyclerView with
    public static List<Item> getData() {
        List<Item> data = new ArrayList<>();
        try {
            JsonNode json = getJSONData(mode == -1 ? "/thepolicies.json" //list of policies file
                                    : (mode == 0 ? "/MPs.json" //list of MPs file
                                    : "/" + url + ".json")), //individual MP's file
                     partiesRecords = objectMapper.createObjectNode(); //initialise new json object
            if (mode == 1) { //individual MP's record mode
                //get the MP's economic and social position
                memberEcon = json.get("economic").doubleValue();
                memberSoc = json.get("social").doubleValue();
                json = json.get("votes"); //get MP's votes

                partiesRecords = getJSONData("/partyish.json"); //the parties' general voting records
                if (partiesRecords.has(party)) { //if party in parties file
                    partiesRecords = partiesRecords.get(party); //get specific party's record
                    //get party's economic and social position
                    partyEcon = partiesRecords.get("economic").doubleValue();
                    partySoc = partiesRecords.get("social").doubleValue();
                } else { //if independent, 1-MP party, Sinn Fein or speaker
                    //set null
                    partyEcon = Double.NaN;
                    partySoc = Double.NaN;
                }
            }

            Iterator<String> itr = json.fieldNames(); //to iterate through the JSON's fields
            while (itr.hasNext()) { //while more field to iterate through
                String next = itr.next(); //get current field
                ArrayNode array = json.withArray(next); //get json array by iterated field name
                for (JsonNode obj : array) { //for each object in the array
                    if (mode == -1) { //quiz mode
                        String policy = obj.get("policy").textValue();
                        int economic = obj.get("economic").intValue(),
                                social = obj.get("social").intValue();
                        if (!(economic == 0 && social == 0)) //if policy has some value
                            //add object to list
                            data.add(new Item(policy,
                                    null,
                                    next, //topic
                                    null,
                                    null,
                                    economic,
                                    social,
                                    false)); //make it unclickable
                    } else if (mode == 0) { //listing MPs
                        data.add(new Item(obj.get("first").textValue(), //first name
                                obj.get("surname").textValue(),
                                next, //party
                                obj.get("seat").textValue(),
                                obj.get("id").textValue(),
                                //if MP doesn't have an economic/social position calculated (if they haven't voted):
                                //if of Sinn Fein, set the position to 11
                                // (so they appear at the bottom of the list of MPs),
                                //else set to 0
                                obj.has("economic") ? obj.get("economic").doubleValue() :
                                        (next.equals("Sinn Féin") ? 11.0 : 0.0),
                                obj.has("social") ? obj.get("social").doubleValue() :
                                        (next.equals("Sinn Féin") ? 11.0 : 0.0),
                                //i.e. only clickable if the MP has voted:
                                obj.has("economic") && obj.has("social")));
                    } else { //individual MP's voting record
                        //Get policy and the MP's % support for it
                        String policy = obj.get("policy").textValue(),
                                percent = obj.get("percent").textValue();

                        String partyPercent = "";
                        //i.e. if in an eligible party, get their party's policy support %
                        if (!Double.isNaN(partyEcon))
                            partyPercent = "\nParty: " + partiesRecords.get(next).get(policy).textValue();
                        data.add(new Item(policy,
                                null,
                                next, //policy's topic
                                percent + partyPercent, //MP support % + Party support %
                                null,
                                Double.NaN,
                                Double.NaN,
                                false)); //unclickable
                    }
                }
            }
            new HeapSort(data, sortBy); //sort generated data
            if (mode == 1) { //only mode with a miscellaneous category (on policies)
                List<Item> misc = new ArrayList<>();
                for (Item m : data) if (m.getCategory().equals("Misc")) misc.add(m); //accumulate all misc policies
                data.removeAll(misc); //remove from data
                data.addAll(misc); //add to end of data. Now all misc policies are at the end of the list
            }
        } catch (DbxException | IOException | NullPointerException e) {
            Log.w("Err on the side of caution", e.getMessage());
            //when internet not connected (handled in Activity on UI-side) or going back and forth very quickly
            //between different loaded modes (causes a NullPointerException)
        }
        return data;
    }

    //retrieve from DropBox by identifier location
    public static JsonNode getJSONData(String location) throws DbxException, IOException {
        //download the requested file and get an ordered stream of bytes
        InputStream fileInputStream = client.files().download(location).getInputStream();
        //return as a JSON tree
        return objectMapper.readTree(fileInputStream);
    }
}
