package com.evandyce.pettinder.api;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.evandyce.pettinder.cards.Animal;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class APIConnector {

    // initialize firebase instance for activity
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String PETFINDER_KEY = "petfinder";
    private final String PETFINDER_KEY_SECRET = "petfinder_secret";


    // urls for api requests
    public static String BASE_PETFINDER_URL = "https://api.petfinder.com/v2/animals?type=dog&limit=100";
    public static final String GET_NEW_TOKEN_URL = "https://api.petfinder.com/v2/oauth2/token";

    // final static string for the new token value
    private static String new_token;

    //context for this class
    Context context;

    // arraylist to store the dogs received from api
    private static List<Animal> animalList;

    public APIConnector(Context context) {
        this.context = context;
        animalList = new ArrayList<>();
        generateNewToken();
    }

    // interface for callback functions
    public interface VolleyResponseListener {
        void onError(String message);

        void onResponse();
    }

    /*
    Querys the API with data passed from SearchFragment inputs
    Calls volleyResponseListener for callback functions and passes the values
     */
    public void getDataFromAPI(String city, String range, String province, VolleyResponseListener volleyResponseListener) {

        // adds the parameters to the API request
        String url = paramaterizeBaseURL(city, range, province);


        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        // function to load response into the arraylist
                        loadDogObjects(response, volleyResponseListener);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                if (error instanceof NetworkError || error instanceof AuthFailureError || error instanceof NoConnectionError || error instanceof TimeoutError) {
                    Toast.makeText(context, "Cannot connect to internet. If you have a connection please exit and restart the app.", Toast.LENGTH_SHORT).show();
                    return;
                } else if(error instanceof ServerError) {
                    Toast.makeText(context, "Server could not be found. Please try again later", Toast.LENGTH_SHORT).show();
                    return;
                } else if (error instanceof ParseError) {
                    Toast.makeText(context, "Parsing error. Please try again later", Toast.LENGTH_SHORT).show();
                    return;
                }
                // if the error is because the token is unauthorized then it passes the message back and generates a new token
                if(error.networkResponse.statusCode == 401) {
                    generateNewToken();
                    getDataFromAPI(city, range, province, volleyResponseListener);
                }
                // city and province do not exist in db
                else if (error.networkResponse.statusCode == 400) {
                    volleyResponseListener.onError("Please enter a valid city");
                }
            }
        })
        {
            /*
            Adds headers to the request uses the token value generated from generateNewToken()
             */
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap headers = new HashMap();
                headers.put("Authorization", "Bearer " + new_token);
                return headers;
            }
        };

        MySingleton.getInstance(context).addToRequestQueue(request);
    }

    /*
    Processes the API response and loads the dogs into the list
    Error checks all of the JSON information and replaces when necessary
     */
    private void loadDogObjects(JSONObject response, VolleyResponseListener volleyResponseListener) {
        try {
            // gets array of animals from api
            JSONArray jsonArray = response.getJSONArray("animals");
            if (jsonArray.length() == 0) {
                volleyResponseListener.onError("There are no dogs available in this city. Please try again later.");
                return;
            }

            // clears old pets from the list
            animalList.clear();

            for (int i = 0; i < jsonArray.length(); i++) {
                // get dog JSON object from the array returned
                JSONObject dog = jsonArray.getJSONObject(i);

                // declaring all variables needed
                String name, city, email, imageURL, age, petfinderURL, description;

                // use conditionals to ensure not null values and if they are then make the changes wanted
                name = dog.getString("name");
                if (name.length() == 0) {   name = "Name not found";    }

                city = dog.getJSONObject("contact").getJSONObject("address").getString("city");
                if (city.length() == 0) {   city = "City not found";    }


                email = dog.getJSONObject("contact").getString("email");
                if (email.length() == 0) {    email = "Email is unavailable";  }

                JSONArray tempArray = dog.getJSONArray("photos");
                if (tempArray.length() > 0) {
                    imageURL = tempArray.getJSONObject(0).getString("large");
                } else {
                    imageURL = "https://www.escapeauthority.com/wp-content/uploads/2116/11/No-image-found.jpg";
                }

                age = dog.getString("age");
                if(age.length() == 0) {  age = "Age not found"; }

                petfinderURL = dog.getString("url");
                if (petfinderURL.length() == 0) {   petfinderURL = "Unable to find URL";    }

                description = dog.getString("description");
                if (description.length() == 0) {    description = "No description available";   }

                System.out.println(dog);
                // make new dog instance and add to the list
                animalList.add(new Animal(name, city, email, age, imageURL, petfinderURL, description));
            }
            volleyResponseListener.onResponse();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /*
    Adds parameters to the base request URL depending on the values passed to it
    Todo: add support for different animal types from spinner
     */
    private String paramaterizeBaseURL(String city, String range, String prov) {

        String temp = BASE_PETFINDER_URL;

        HashMap<String, String> provinces = new HashMap<>();
        provinces.put("British Columbia", "BC");
        provinces.put("Alberta", "AB");
        provinces.put("Saskatchewan", "SK");
        provinces.put("Manitoba", "MB");
        provinces.put("Ontario", "ON");
        provinces.put("Quebec", "QC");
        provinces.put("Newfoundland and Labrador", "NL");
        provinces.put("Nova Scotia", "NS");
        provinces.put("New Brunswick", "NB");
        provinces.put("P.E.I.", "PE");
        provinces.put("Yukon", "YT");
        provinces.put("Northwest Territories", "NT");
        provinces.put("Nunavat", "NU");

        String province = provinces.get(prov);

        if (city.length() != 0 && range.length() != 0) {
            temp += "&location=" + city + ", " + province + "&distance=" + range;
        } else if (city.length() > 0) {
            temp += "&location=" + city + ", " + province;
        } else if (range.length() > 0) {
            temp += "&distance=" + range;
        } else {
            temp += "&location=" + province;
        }

        return temp;
    }

    /*
    Makes a new token and assigns it to the static variable new_token
    makes a post request to the API and gets the new token back
     */
    private void generateNewToken() {
        DocumentReference documentReference = db.document("keys/ar6S9VvAdD5z6qCkE57X");

        documentReference.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                String client_id = documentSnapshot.getString(PETFINDER_KEY);
                String secret_id = documentSnapshot.getString(PETFINDER_KEY_SECRET);


                sendRequest(client_id, secret_id);

                Log.d("GettingKeys", secret_id + " " + client_id);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("GettingKeys", "Uh oh this was a failure");
            }
        });
    }

    private void sendRequest(String client, String secret) {
        String url = "https://api.petfinder.com/v2/oauth2/token";

        JSONObject postData = new JSONObject();
        try {
            postData.put("grant_type", "client_credentials");
            postData.put("client_id", client);
            postData.put("client_secret", secret);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, postData,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            new_token = response.getString("access_token");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                    }
                });

        MySingleton.getInstance(context).addToRequestQueue(request);
    }

    public static List<Animal> getAnimalList() {
        return animalList;
    }
}
