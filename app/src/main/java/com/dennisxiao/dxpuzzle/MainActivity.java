package com.dennisxiao.dxpuzzle;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.dennisxiao.dxpuzzle.Utility.ImageGridConstant;
import com.dennisxiao.dxpuzzle.Utility.Utility;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.json.JSONArray;
import org.json.JSONException;

public class MainActivity extends AppCompatActivity implements OnClickListener, GoogleApiClient.OnConnectionFailedListener {

    // the php page that handles ranking
    private static final String PHP_URL_CHECK_RANKING = "http://www.dennisxiao.com/projects/dxpuzzle/dxpuzzle_checkranking.php";
    private static final String PHP_URL_UPDATE_SCORE = "http://www.dennisxiao.com/projects/dxpuzzle/dxpuzzle_updatescore.php";

    // create a local variable for identifying the class where the log statements come from
    private final static String LOG_TAG = MainActivity.class.getSimpleName();

    /* Request code used to invoke sign in user interactions. */
    private static final int RC_SIGN_IN = 9001;

    // create an listener for the button mediaPlayer
    private MediaPlayer.OnCompletionListener mCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            Utility.releaseMediaResource(mediaPlayer);
        }
    };

    // Google sign in resources
    private SignInButton mSignInButton;
    private Button mSignOutButton;
    private TextView mStatus;
    private GoogleApiClient mGoogleApiClient;

    // ImageButton for displaying the puzzle image
    private ImageButton ib_00, ib_01, ib_02,
            ib_10, ib_11, ib_12,
            ib_20, ib_21, ib_22;

    // other resources
    private TextView puzzleTime;
    private Button btn_restart, btn_ranking, btn_cheat;
    private MediaPlayer buttonPlayer;

    // initialize the width and height of the puzzle square
    private int squareWidth = 3;
    private int squareHeight = 3;

    // initialize the blank position and the blank ImageButton id, by default the last grid is blank
    private int blankPos = 8;
    private int blankImgId = R.id.ib_02_02;

    // initialize an int array to store the order of the images
    private int[] imageOrder = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};

    // retrieve a random image
    private int[] imageId = ImageGridConstant.getImageGrid();

    // initialize variables for recording time
    private boolean timeRun = true;

    // record the time of the game
    private int time = 0;

    // keep track of if the game should finish
    private boolean gameFinish;

    // create a thread to record time
    private Thread gameTimeThread = new Thread() {

        public void run() {

            while (true) {
                String strTime = time + "";
                // create a Message instance
                Message msg = Message.obtain();
                // store the time
                msg.obj = strTime;
                // send the data to handler
                mHandler.sendMessage(msg);

                try {
                    Thread.sleep(1000);
                    time++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        } // end of run()

    }; // end of Thread()

    // create a handler to handle displaying the time
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            String time = (String) msg.obj;
            puzzleTime.setText("Timer: " + time);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Google sign in / sign out
        mSignInButton = (SignInButton) findViewById(R.id.sign_in_button);
        mSignOutButton = (Button) findViewById(R.id.sign_out_button);
        mStatus = (TextView) findViewById(R.id.sign_in_status);
        mSignInButton.setOnClickListener(this);
        mSignOutButton.setOnClickListener(this);

        // by default hide the sign out button
        findViewById(R.id.sign_out_button).setVisibility(View.GONE);

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        // Build a GoogleApiClient with access to the Google Sign-In API and the
        // options specified by gso.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        // initialize the game
        initGame();

        // start the thread
        gameTimeThread.start();

    } // end of onCreate()

    @Override
    protected void onStop() {
        super.onStop();

        // MediaPlayer resource should be released when a user is no longer within the app
        Utility.releaseMediaResource(buttonPlayer);
    }

    // initialize the game
    private void initGame() {

        puzzleTime = (TextView) findViewById(R.id.puzzleTime);
        btn_restart = (Button) findViewById(R.id.btn_reset);
        btn_ranking = (Button) findViewById(R.id.btn_ranking);
        btn_cheat = (Button) findViewById(R.id.btn_cheating);

        ib_00 = (ImageButton) findViewById(R.id.ib_00_00);
        ib_01 = (ImageButton) findViewById(R.id.ib_00_01);
        ib_02 = (ImageButton) findViewById(R.id.ib_00_02);
        ib_10 = (ImageButton) findViewById(R.id.ib_01_00);
        ib_11 = (ImageButton) findViewById(R.id.ib_01_01);
        ib_12 = (ImageButton) findViewById(R.id.ib_01_02);
        ib_20 = (ImageButton) findViewById(R.id.ib_02_00);
        ib_21 = (ImageButton) findViewById(R.id.ib_02_01);
        ib_22 = (ImageButton) findViewById(R.id.ib_02_02);

        // bind onClick listeners to ImageButton
        ib_00.setOnClickListener(puzzleButtonOnClickListener);
        ib_01.setOnClickListener(puzzleButtonOnClickListener);
        ib_02.setOnClickListener(puzzleButtonOnClickListener);
        ib_10.setOnClickListener(puzzleButtonOnClickListener);
        ib_11.setOnClickListener(puzzleButtonOnClickListener);
        ib_12.setOnClickListener(puzzleButtonOnClickListener);
        ib_20.setOnClickListener(puzzleButtonOnClickListener);
        ib_21.setOnClickListener(puzzleButtonOnClickListener);
        ib_22.setOnClickListener(puzzleButtonOnClickListener);

        // shuffle the image order
        random();

        // after shuffling the array, set images to the ImageViews
        displayGrids(imageId);

        // reshuffle the girds when the restart button is clicked
        btn_restart.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                // reset the game
                time = 0;
                timeRun = true;
                gameFinish = false;
                puzzleTime.setText("Timer: 0");
                puzzleTime.setVisibility(View.VISIBLE);

                // get a random image
                imageId = ImageGridConstant.getImageGrid();

                // reset the blank pos and blank image id
                blankPos = 8;
                blankImgId = R.id.ib_02_02;

                // reset the image order
                imageOrder = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};

                // reset the grid visibility
                resetGridVisibility();

                // shuffle the image order
                random();

                // after shuffling the array, set images to the ImageViews
                displayGrids(imageId);

                // make all the buttons clickable
                ib_00.setClickable(true);
                ib_01.setClickable(true);
                ib_02.setClickable(true);
                ib_10.setClickable(true);
                ib_11.setClickable(true);
                ib_12.setClickable(true);
                ib_20.setClickable(true);
                ib_21.setClickable(true);
                ib_22.setClickable(true);

                // enable cheat button
                btn_cheat.setEnabled(true);

                // play a sound and release it when finished
                buttonPlayer = MediaPlayer.create(MainActivity.this, R.raw.restart);
                buttonPlayer.start();
                buttonPlayer.setOnCompletionListener(mCompletionListener);

            }
        });

        // display a ranking when ranking button is clicked
        btn_ranking.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                // get the JSON string from the php file
                getJSON(PHP_URL_CHECK_RANKING);
            }
        });

        // re-organize the image when cheat button is clicked
        btn_cheat.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                // reset the blank pos and blank image id
                blankPos = 8;
                blankImgId = R.id.ib_02_02;

                // reset the image order
                imageOrder = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};

                // reset grid visibility
                resetGridVisibility();

                // set images to the grids
                displayGrids(imageId);

            }
        });

    } // end of onCreate()

    // create an onclick listener for puzzle button
    View.OnClickListener puzzleButtonOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // check which ImageButton is clicked
            switch (v.getId()) {
                case R.id.ib_00_00:
                    move(R.id.ib_00_00, 0);
                    break;
                case R.id.ib_00_01:
                    move(R.id.ib_00_01, 1);
                    break;
                case R.id.ib_00_02:
                    move(R.id.ib_00_02, 2);
                    break;
                case R.id.ib_01_00:
                    move(R.id.ib_01_00, 3);
                    break;
                case R.id.ib_01_01:
                    move(R.id.ib_01_01, 4);
                    break;
                case R.id.ib_01_02:
                    move(R.id.ib_01_02, 5);
                    break;
                case R.id.ib_02_00:
                    move(R.id.ib_02_00, 6);
                    break;
                case R.id.ib_02_01:
                    move(R.id.ib_02_01, 7);
                    break;
                case R.id.ib_02_02:
                    move(R.id.ib_02_02, 8);
                    break;
            }
        }
    };

    /**
     * swap clicked ImageView position for the blank view one
     *
     * @param clickImageBtnID the Id of the clicked button
     * @param clickPos        an int representing the position of the clicked button, ranging from 0 - 8
     */
    public void move(int clickImageBtnID, int clickPos) {

        // keep track of the imageOrder array and clickPos
        Log.i(LOG_TAG, "imageOrder before swap = " + Arrays.toString(imageOrder));
        Log.i(LOG_TAG, "clickPos = " + clickPos);

        // calculate the clicked image button positions
        int clickPosX = clickPos / squareWidth;
        Log.i(LOG_TAG, "clickPosX = " + clickPosX);
        int clickPosY = clickPos % squareHeight;
        Log.i(LOG_TAG, "clickPosY = " + clickPosY);

        // calculate the blank image button positions
        int blankPosX = blankPos / squareWidth;
        Log.i(LOG_TAG, "blankPosX = " + blankPosX);
        int blankPosY = blankPos % squareHeight;
        Log.i(LOG_TAG, "blankPosY = " + blankPosY);

        // calculate the distance between the clicked and the blank buttons
        int distanceX = Math.abs(clickPosX - blankPosX);
        int distanceY = Math.abs(clickPosY - blankPosY);
        Log.i(LOG_TAG, "distanceX = " + distanceX);
        Log.i(LOG_TAG, "distanceY = " + distanceY);

        // check if the clicked button is next to the blank button
        if ((distanceX == 0 && distanceY == 1) || (distanceX == 1 && distanceY == 0)) {

            // play a sound and release it when finished
            buttonPlayer = MediaPlayer.create(MainActivity.this, R.raw.button_click_move);
            buttonPlayer.start();
            buttonPlayer.setOnCompletionListener(mCompletionListener);

            // find the clicked ImageButton
            ImageButton clickedButton = (ImageButton) findViewById(clickImageBtnID);
            // set the clicked button to invisible
            clickedButton.setVisibility(View.INVISIBLE);

            // find the blank ImageButton
            ImageButton blankButton = (ImageButton) findViewById(blankImgId);
            // change the blank ImageButton to the clicked ImageButton
            blankButton.setImageDrawable(ResourcesCompat.getDrawable(getResources(), imageId[imageOrder[clickPos]], null));
            // set the blank ImageButton visible
            blankButton.setVisibility(View.VISIBLE);

            // swap the clickPos with the blankPos
            swap(clickPos, blankPos);
            // update the blankPos
            blankPos = clickPos;
            // update the blank ImageButton id
            blankImgId = clickImageBtnID;

            // keep track of the imageOrder array
            Log.i(LOG_TAG, "imageOrder after swap = " + Arrays.toString(imageOrder));
            Log.i(LOG_TAG, "----------");

            // check if the game is finished
            gameFinish();

        } else { // alert the users if they are not clicking images next to the blank ImageButton

            // play a sound and release it when finished
            buttonPlayer = MediaPlayer.create(MainActivity.this, R.raw.button_click_nomove);
            buttonPlayer.start();
            buttonPlayer.setOnCompletionListener(mCompletionListener);

            Toast.makeText(MainActivity.this, "Please click on image next to the blank", Toast.LENGTH_SHORT).show();
        }
    }

    // make the elements of imageOrder array in random order
    private void random() {

        // declare two variables to hold random numbers
        int ran1, ran2;

        // shuffle the imageOrder array 25 times
        for (int i = 0; i < 25; i++) {
            // initialize the first random number ranging from 0 - 7
            // this keeps the last element in the array untouched, so always hiding the last piece
            ran1 = new Random().nextInt(8);
            ran2 = new Random().nextInt(8);

            // if the two random indices are different, swap them in imageOrder array
            if (ran1 != ran2) {
                swap(ran1, ran2);
            }
        } // end of for loop

    } // end of random()

    /**
     * swaps two indices of imageOrder array
     *
     * @param ran1 an int representing the index in an array
     * @param ran2 an int representing the index in an array
     */
    private void swap(int ran1, int ran2) {
        int temp = imageOrder[ran1];
        imageOrder[ran1] = imageOrder[ran2];
        imageOrder[ran2] = temp;
    }

    // this method resets all grids visibility
    private void resetGridVisibility() {
        ib_00.setVisibility(View.VISIBLE);
        ib_01.setVisibility(View.VISIBLE);
        ib_02.setVisibility(View.VISIBLE);
        ib_10.setVisibility(View.VISIBLE);
        ib_11.setVisibility(View.VISIBLE);
        ib_12.setVisibility(View.VISIBLE);
        ib_20.setVisibility(View.VISIBLE);
        ib_21.setVisibility(View.VISIBLE);
        ib_22.setVisibility(View.INVISIBLE);
    }

    /**
     * set images to all the grids
     *
     * @param imageId an int array holding all the images
     */
    private void displayGrids(int[] imageId) {
        ib_00.setImageDrawable(ResourcesCompat.getDrawable(getResources(), imageId[imageOrder[0]], null));
        ib_01.setImageDrawable(ResourcesCompat.getDrawable(getResources(), imageId[imageOrder[1]], null));
        ib_02.setImageDrawable(ResourcesCompat.getDrawable(getResources(), imageId[imageOrder[2]], null));
        ib_10.setImageDrawable(ResourcesCompat.getDrawable(getResources(), imageId[imageOrder[3]], null));
        ib_11.setImageDrawable(ResourcesCompat.getDrawable(getResources(), imageId[imageOrder[4]], null));
        ib_12.setImageDrawable(ResourcesCompat.getDrawable(getResources(), imageId[imageOrder[5]], null));
        ib_20.setImageDrawable(ResourcesCompat.getDrawable(getResources(), imageId[imageOrder[6]], null));
        ib_21.setImageDrawable(ResourcesCompat.getDrawable(getResources(), imageId[imageOrder[7]], null));
        ib_22.setImageDrawable(ResourcesCompat.getDrawable(getResources(), imageId[imageOrder[8]], null));
    }

    // check if the game is finished
    private void gameFinish() {

        gameFinish = true;

        // iterate through each grid to check if all the images are in the right places
        for (int i = 0; i < 9; i++) {
            if (imageOrder[i] != i) {
                // if any of the image is not in the right place, the game continues
                gameFinish = false;
            }
        }

        // game is finished
        if (gameFinish) {

            timeRun = false;

            // make all the buttons un-clickable
            ib_00.setClickable(false);
            ib_01.setClickable(false);
            ib_02.setClickable(false);
            ib_10.setClickable(false);
            ib_11.setClickable(false);
            ib_12.setClickable(false);
            ib_20.setClickable(false);
            ib_21.setClickable(false);
            ib_22.setClickable(false);

            // disable cheat button
            btn_cheat.setEnabled(false);

            // hide the time
            puzzleTime.setVisibility(View.INVISIBLE);

            // display the last hidden piece of the puzzle
            ib_22.setImageDrawable(ResourcesCompat.getDrawable(getResources(), imageId[8], null));
            ib_22.setVisibility(View.VISIBLE);

            // display the congratulation message
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage("Congratulation! You finished the puzzle with " + time + " seconds!").setPositiveButton("OK", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            builder.create();
            builder.show();

            // retrieve the user name
            String username = mStatus.getText().toString();

            if (username.equals(" ")) {
                username = "Player";
            }

            // upload the score to database
            updateScore(username, Integer.toString(time));

        }

    } // end of gameFinish()

    // this method updates the player's score to database
    private void updateScore(final String playerName, final String score) {

        // initialize a requestQuest
        RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());

        StringRequest request = new StringRequest(Request.Method.POST, PHP_URL_UPDATE_SCORE, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {

                System.out.println(response.toString());
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        })

                // an inner class
        {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> parameters = new HashMap<String, String>();
                parameters.put("username", playerName);
                parameters.put("score", score);
                return parameters;
            }
        };

        requestQueue.add(request);

    } // end of updateScore(final String playerName, final String score)

    // this method get JSON from a php page
    private void getJSON(String url) {

        class GetJSON extends AsyncTask<String, Void, String> {

            ProgressDialog loading;

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                loading = ProgressDialog.show(MainActivity.this, "Please Wait...", null, true, true);
            }

            @Override
            protected String doInBackground(String... params) {

                String uri = params[0];

                BufferedReader bufferedReader = null;

                try {
                    URL url = new URL(uri);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    StringBuilder sb = new StringBuilder();

                    bufferedReader = new BufferedReader(new InputStreamReader(con.getInputStream()));

                    String json;
                    while ((json = bufferedReader.readLine()) != null) {
                        sb.append(json + "\n");
                    }

                    return sb.toString().trim();

                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                loading.dismiss();

                try {

                    // create a JSONArray
                    JSONArray array = new JSONArray(s);

                    // extract the username and score from the JSONArray
                    displayRanking(array.getJSONObject(0).getString("username"),
                            array.getJSONObject(0).getString("score"),
                            array.getJSONObject(1).getString("username"),
                            array.getJSONObject(1).getString("score"),
                            array.getJSONObject(2).getString("username"),
                            array.getJSONObject(2).getString("score"));

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }

        GetJSON gj = new GetJSON();
        gj.execute(url);
    }

    // this method shows a dialog
    private void displayRanking(String name1, String playerScore1,
                                String name2, String playerScore2,
                                String name3, String playerScore3) {

        // custom dialog
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.custom_dialog);
        dialog.setTitle("DXPuzzle Ranking");

        // set the custom dialog components - text, image and button
        TextView username1 = (TextView) dialog.findViewById(R.id.username1);
        TextView score1 = (TextView) dialog.findViewById(R.id.score1);
        TextView username2 = (TextView) dialog.findViewById(R.id.username2);
        TextView score2 = (TextView) dialog.findViewById(R.id.score2);
        TextView username3 = (TextView) dialog.findViewById(R.id.username3);
        TextView score3 = (TextView) dialog.findViewById(R.id.score3);

        username1.setText(name1 + " - ");
        score1.setText(playerScore1 + "s");
        username2.setText(name2 + " - ");
        score2.setText(playerScore2 + "s");
        username3.setText(name3 + " - ");
        score3.setText(playerScore3 + "s");

        dialog.show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sign_in_button:
                signIn();
                break;
            case R.id.sign_out_button:
                signOut();
                break;
        }
    } // end of onClick()

    private void signIn() {
        // initialize a sign in intent
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut() {
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {

                        // hide the sign out button
                        findViewById(R.id.sign_out_button).setVisibility(View.GONE);
                        findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);

                        // reset the sign in text
                        mStatus.setText(" ");

                    }
                });
    }

    private void handleSignInResult(GoogleSignInResult result) {
        Log.d(LOG_TAG, "handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            // Signed in successfully, show authenticated UI.
            GoogleSignInAccount acct = result.getSignInAccount();
            mStatus.setText(acct.getDisplayName());

            // display the sign out button
            findViewById(R.id.sign_out_button).setVisibility(View.VISIBLE);
            // hide the sign in button
            findViewById(R.id.sign_in_button).setVisibility(View.GONE);

        } else {
            // Signed out, show unauthenticated UI.
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleSignInResult(result);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

}