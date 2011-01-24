/*
 *  (c) Copyright (c) 2010 Mridang Agarwalla
 *  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Map;
import java.util.HashMap;

import org.jsoup.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Custom exception class for situations when an invalid parameter is requested
 */
class WrongParameterException extends RuntimeException {
    public WrongParameterException(String message) {
        super(message);
    }
}

/**
 * Te Java class for accessing the IMDB page for a movie/show
 */
public class JIMDB {

    /** The most common information for an IMDb id.
     */
    private String sIMDBURL;
    private String sIMDBID;
    private String sTitle;
    private String sYear;
    private String sRating;
    private String sVotes;
    private String sGenre;
    private String sRuntime;
    private Map<String, String> sCinemaDate;
    private Map<String, String> sEpisodes;
    /** The user agent string to use when accessing IMDB
     */
    private final String USER_AGENT = "Mozilla/5.0 (Windows; U; Windows NT 6.1;"
    + " en-GB; rv:1.9.2.13) Gecko/20101203 Firefox/3.6.13";
    /** A flag to indicate whether it is a movie or a tv show
     */
    private String runMode;

   /**
    * Gets the episodes of a show
    *
    * @param  doc The parsed IMDb page
    * @return     Returns the episodes of a show
    */
    private Map<String, String>  getinetEpisodes(Document doc) {
        Element keyNode;
        Element valueNode;
        Map<String, String> episodesList = new HashMap<String, String>();

        for (Element x : doc.getElementsByClass("filter-all")) {
            keyNode = x.getElementsByTag("h3").first();
            valueNode = x.getElementsByTag("h3").first().children().first();

            episodesList.put(keyNode.ownText().replaceAll(":", ""), valueNode.text());
        }
        return episodesList;
    }

   /**
    * Gets the release date of a movie/show
    *
    * @param  doc The parsed IMDb page
    * @return     Returns the release date of the movie
    */
    private Map<String, String> getinetCinemaDate(Document doc) {
        Element keyNode;
        Element valueNode;
        Map<String, String> reldateList = new HashMap<String, String>();

        for (Element x : doc.getElementById("tn15content").getElementsByTag("table").first().getElementsByTag("tr")) {
            if (x.getElementsByTag("td").size() > 0) {
                keyNode = x.getElementsByTag("td").get(0);
                valueNode = x.getElementsByTag("td").get(1);

                reldateList.put(keyNode.text(), valueNode.text());
            }
        }
        return reldateList;
    }

   /**
    * Gets the title of a movie/show
    *
    * @param  doc The parsed IMDb page
    * @return     Returns the title
    */
    private String getinetTitle(Document doc) {
        Element titleNode = doc.select("title").first();
        return titleNode.text().replaceAll("\\s\\(.*\\).*", "");
    }

   /**
    * Gets the year of a movie/show
    *
    * @param  doc The parsed IMDb page
    * @return     Returns the year
    */
    private String getinetYear(Document doc) {
        Element titleNode = doc.select("title").first();
        return titleNode.text().replaceAll(".*?\\((\\d{4})\\).*", "$1");
    }

   /**
    * Gets the rating of a movie/show
    *
    * @param  doc The parsed IMDb page
    * @return     Returns the of the movie/show
    */
    private String getinetRating(Document doc) {
        Element titleNode = doc.getElementsByClass("rating-rating").first();
        return titleNode.text().replaceAll("(\\d\\.\\d)/10", "$1");
    }

   /**
    * Gets the number of votes of a movie/show
    *
    * @param  doc The parsed IMDb page
    * @return     Returns the number of votes of the movie/show
    */
    private String getinetVotes(Document doc)
    {
        Element sbNode = doc.getElementsByClass("star-box").first();
        return sbNode.child(2).text().replaceAll( "[^\\d]", "" );
    }

   /**
    * Gets the genre of a movie/show
    *
    * @param  doc The parsed IMDb page
    * @return     Returns the genre of the movie/show
    */
    private String getinetGenre(Document doc) {
        Element sbNode = doc.getElementsByClass("infobar").first();
        return sbNode.getElementsByAttributeValueStarting("href", "/genre/").text().replaceAll(" ", ", ");
    }

   /**
    * Gets the runtime of a movie/show
    *
    * @param  doc The parsed IMDb page
    * @return     Returns the runtime of the movie/show
    */
    private String getinetRuntime(Document doc) {
        Element sbNode = doc.getElementsByClass("infobar").first();
        return sbNode.text().replaceAll(".*?(\\d*)\\smin.*", "$1");
    }

   /**
    * Gets the baisc information for a movie/show
    *
    * @param  sID The IMDb ID of the page that needs to be parsed
    * @return
    */
    private void getObjectByID(String sID) {
        try {
            StringBuilder httpResponse;
            URL httpURL;
            URLConnection httpFetcher;
            BufferedReader httpBuffer;
            InputStreamReader httpStream;
            String httpResponseLine;
            Document doc;

            httpResponse = new StringBuilder();
            httpURL = new URL("http://akas.imdb.com/title/" + sID);
            httpFetcher = httpURL.openConnection();
            httpFetcher.setRequestProperty("User-Agent", USER_AGENT);
            httpStream = new InputStreamReader(httpFetcher.getInputStream());
            httpBuffer = new BufferedReader(httpStream);
            while ((httpResponseLine = httpBuffer.readLine()) != null) {
                httpResponse.append(httpResponseLine);
            }

            doc = Jsoup.parseBodyFragment(httpResponse.toString(), "UTF-8");
            sIMDBID = sID;
            sIMDBURL = httpURL.toString();
            sTitle = getinetTitle(doc);
            sYear = getinetYear(doc);
            sRating = getinetRating(doc);
            sVotes = getinetVotes(doc);
            sGenre = getinetGenre(doc);
            sRuntime = getinetRuntime(doc);

        } catch (MalformedURLException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
    }

   /**
    * Parses and IMDb page and exracts all the information
    *
    * @param  sID The IMDb ID of the page that needs to be parsed
    * @return
    */
    public void getTVShowByID(String sID) {
        runMode = "TV-SHOW";
        getObjectByID(sID);

        try {
            StringBuilder httpResponse;
            URL httpURL;
            URLConnection httpFetcher;
            BufferedReader httpBuffer;
            InputStreamReader httpStream;
            String httpResponseLine;
            Document doc;

            httpResponse = new StringBuilder();
            httpURL = new URL("http://akas.imdb.com/title/" + sID + "/episodes");
            httpFetcher = httpURL.openConnection();
            httpFetcher.setRequestProperty("User-Agent", USER_AGENT);
            httpStream = new InputStreamReader(httpFetcher.getInputStream());
            httpBuffer = new BufferedReader(httpStream);
            while ((httpResponseLine = httpBuffer.readLine()) != null) {
                httpResponse.append(httpResponseLine);
            }

            doc = Jsoup.parseBodyFragment(httpResponse.toString());
            sEpisodes = getinetEpisodes(doc);

        } catch (MalformedURLException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
    }

   /**
    * Parses and IMDb page and exracts all the information
    *
    * @param  sID The IMDb ID of the page that needs to be parsed
    * @return
    */
    public void getMovieByID(String sID) {
        runMode = "MOVIE";
        getObjectByID(sID);

        try {
            StringBuilder httpResponse;
            URL httpURL;
            URLConnection httpFetcher;
            BufferedReader httpBuffer;
            InputStreamReader httpStream;
            String httpResponseLine;
            Document doc;

            httpResponse = new StringBuilder();
            httpURL = new URL("http://akas.imdb.com/title/" + sID + "/releaseinfo");
            httpFetcher = httpURL.openConnection();
            httpFetcher.setRequestProperty("User-Agent", USER_AGENT);
            httpStream = new InputStreamReader(httpFetcher.getInputStream());
            httpBuffer = new BufferedReader(httpStream);
            while ((httpResponseLine = httpBuffer.readLine()) != null) {
                httpResponse.append(httpResponseLine);
            }

            doc = Jsoup.parseBodyFragment(httpResponse.toString());
            sCinemaDate = getinetCinemaDate(doc);

        } catch (MalformedURLException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
    }

   /**
    * Gets the full IMDb URL of the page
    *
    * @return     Returns the full IMDb URL of the page
    */
    public String getURL() {
         return sIMDBURL;
    }

   /**
    * Gets the IMDb id for the page
    *
    * @return     Returns the IMDb id of the page
    */
    public Integer getID() {
        return Integer.parseInt(sIMDBID);
    }

   /**
    * Gets the title of the movie/show
    *
    * @return     Returns the title of the movie/show
    */
    public String getTitle() {
       return sTitle;
    }

   /**
    * Gets the year of the movie/show
    *
    * @return     Returns the
    */
    public String getYear() {
        return sYear;
    }

   /**
    * Gets the rating of the movie/show
    *
    * @return     Returns the rating of the movie/show
    */
    public Double getRating() {
       return Double.parseDouble(sRating);
    }

   /**
    * Gets the number of votes of the movie
    *
    * @return     Returns the number of votes of the movie/show
    */
    public Integer getVotes() {
       return Integer.parseInt(sVotes);
    }

   /**
    * Gets the genre of the movie/show
    *
    * @return     Returns the genre of the movie/show
    */
    public String getGenre() {
        return sGenre;
    }

   /**
    * Gets the runtime of the movie/show
    *
    * @return     Returns the runtime of the movie/show
    */
    public Integer getRuntime() {
        return Integer.parseInt(sRuntime);
    }

   /**
    * Gets the release date of the movie/show
    *
    * @param  seasonNo  The country for which you need to fetch the date
    * @param  episodeNo The episode for which to fetch the date
    * @return           Returns the release date of the movie/show
    */
    public String getEpisode(Integer seasonNo, Integer episodeNo) {
       if (runMode.equals("MOVIE")) {
            throw new WrongParameterException("This parameter is not available");
        }

        if (!sEpisodes.containsKey("Season " + seasonNo + ", Episode " + episodeNo)) {
            return null;
        }

        return sEpisodes.get("Season " + seasonNo + ", Episode " + episodeNo);
    }

   /**
    * Gets the release date of the movie/show
    *
    * @param  countryName The country for which you need to fetch the date
    * @return             Returns the release date of the movie/show
    */
    public Date getCinemaDate(String countryName) {
        if (runMode.equals("TV-SHOW")) {
            throw new WrongParameterException("This parameter is not available");
        }

        if (!sCinemaDate.containsKey(countryName)) {
            return null;
        }

        try {
            return (Date) (new SimpleDateFormat("dd MMM yyyy").parse(sCinemaDate.get(countryName)));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

}