package com.manasrawat.bigtent;

import static com.manasrawat.bigtent.Activity.mode;

//object holding item
public class Item {
    private String headingMain, headingExtra, category, detail, url;
    private double econ, soc;
    private boolean active;

    public Item(String headingMain, String headingExtra, String category, String detail, String url, double econ, double soc, boolean active) {
        this.headingMain = headingMain; //firstName or policy
        this.headingExtra = headingExtra; //surname
        this.category = category; //party or policy topic
        this.detail = detail; //constituency or MPs' policy support % + their party's support %
        this.url = url; //MP ID
        this.econ = econ; //MP or policy's economic position
        this.soc = soc; //MP or policy's social position
        this.active = active; //whether item can be clicked
    }

    //series of getters and setters

    public String getHeadingMain() {
        return headingMain; //to retrieve property
    }

    public void setHeadingMain(String headingMain) { //to set property
        this.headingMain = headingMain; //updates property globally
    }

    public String getHeadingExtra() {
        return headingExtra;
    }

    public void setHeadingExtra(String headingExtra) {
        this.headingMain = headingExtra;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public double getEcon() {
        return econ;
    }

    public void setEcon(double econ) {
        this.econ = econ;
    }

    public double getSoc() {
        return soc;
    }

    public void setSoc(double soc) {
        this.soc = soc;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    //when dataset of items is being sorted as per multiple criteria (with 'fallbacks') to ensure no data matches
    //in order to prevent incorrect sorting
    public String getSorter(int type) { //type = sort mode
        switch (type) {
            //by surname (first name and seat are fallbacks); only used in mode 0
            default: case 0: return headingExtra + headingMain + detail;
            case 1: return headingMain + headingExtra + detail; //by first name or policy; only used in mode 0
            case 2: return detail; //by seat
            //by party or policy topic, with fallbacks of surname + seat (if mode 0, MPs list)
            //or fallback of just MP and their party's policy support (mode 0)
            case 5: return category + (mode == 0 ? headingExtra : "") + headingMain + (mode == -1 ? "" : detail);
        }
    }

}

