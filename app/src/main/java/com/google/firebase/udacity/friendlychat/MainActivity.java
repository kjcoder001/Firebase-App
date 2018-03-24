
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final int RC_SIGN_IN =1 ;
    private static final int RC_PHOTO_PICKER = 2;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;


    private FirebaseDatabase firebaseDatabase; //instance of the database,our entry point to the db
    private DatabaseReference messagesDatabseReference;//reference for a particular section of the db
    private ChildEventListener childEventListener;//event listener for fire base real time database,i.e the listener can
    // prompt us whenever there is change in the database.
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private FirebaseStorage storage;
    private StorageReference storageReference;

    private String mUsername;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;


        firebaseDatabase=FirebaseDatabase.getInstance();//gets the instance of the database
        messagesDatabseReference=firebaseDatabase.getReference().child("messages");// creates a reference in the
        //database for "messages"  and gets access to it.Specifically interested in messages portion of the database
        firebaseAuth=FirebaseAuth.getInstance();//gets the instance of firebaseAuth
        storage=FirebaseStorage.getInstance();
        storageReference=storage.getReference().child("photos");




        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);


        mProgressBar.setVisibility(ProgressBar.INVISIBLE);



        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY,true);
                startActivityForResult(intent.createChooser(intent,"Completer action using"),RC_PHOTO_PICKER);
            }
        });



        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


                FriendlyMessage message=new FriendlyMessage(mMessageEditText.getText().toString(),mUsername,null);
                messagesDatabseReference.push().setValue(message);


                // Clear input box
                mMessageEditText.setText("");
            }
        });

        // child event listener for a "child" in our database ,e.g here its "messages".
        //automatically generates an anonymous class to override unimplemented methods.

        authStateListener=new FirebaseAuth.AuthStateListener() {
            /**
             *
             * @param firebaseAuth:- the difference between firebaseAuth in the params and the one we initialized above
             *                    is that this firebaseAuth variable will definitely contain info whether at the moment
             *                    the user is authenticated or not.
             *                    It has 2 states , signed-in or signed-out.
             */
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {

                FirebaseUser user=firebaseAuth.getCurrentUser();
                if(user!=null)
                {
                    //signed in
                    Toast.makeText(MainActivity.this, "SIGNED IN!", Toast.LENGTH_SHORT).show();
                    onSignedInInitialize(user.getDisplayName());
                }
                else
                {
                    //signed out
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.EmailBuilder().build(),
                                            new AuthUI.IdpConfig.GoogleBuilder().build()))
                                            .build(),
                            RC_SIGN_IN);
                }
            }
        };
    }
    @Override
   public void onActivityResult(int requestCode, int resultCode, Intent data) {
                super.onActivityResult(requestCode, resultCode, data);
                if (requestCode == RC_SIGN_IN) {
                    if (resultCode == RESULT_OK) {
                        // Sign-in succeeded, set up the UI
                        Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show();
                    } else if (resultCode == RESULT_CANCELED) {
                        // Sign in was canceled by the user, finish the activity
                        Toast.makeText(this, "Sign in canceled", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }else if(requestCode==RC_PHOTO_PICKER && resultCode==RESULT_OK){

                        Uri imageUri=data.getData();
                        StorageReference reference=storageReference.child(imageUri.getLastPathSegment());
                        // the name with which the file would be stored(is currently stored in our device gallery)
                        // in the db folder is extracted out like this.
                        // e.g if the uri is http://server/photos/arsenal ; so arsenal would be taken from the the path

                        reference.putFile(imageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                                Uri downloadUrl=taskSnapshot.getDownloadUrl();
                                Log.i("tag2",downloadUrl.toString());
                                FriendlyMessage message=new FriendlyMessage(null,mUsername,downloadUrl.toString());

                                messagesDatabseReference.push().setValue(message);
                            }
                        });
                    }

                }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==R.id.sign_out_menu)
        {
            AuthUI.getInstance().signOut(this);
            return true;
        }
        else
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(authStateListener!=null)
        firebaseAuth.removeAuthStateListener(authStateListener);

        detachDatabseReadListener();
        mMessageAdapter.clear();
    }

    @Override
    protected void onResume() {
        super.onResume();
        firebaseAuth.addAuthStateListener(authStateListener);
    }

    public void onSignedInInitialize(String name)
    {
        mUsername=name;
        attachDatabaseListeners();

    }
    public void onSignedOutCleanup()
    {
        mUsername=ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabseReadListener();

    }

    private void attachDatabaseListeners()
    {
        if(childEventListener==null) {

            childEventListener = new ChildEventListener() {

                /**
                 * @param dataSnapshot:-contains data from the db at a specific location at the exact time the listener is
                 *                               triggered.It contains the message that  has been added .
                 * @param s
                 */
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    // gets called whenever a new message is inserted into the messages list in our rtdb.
                    // its also triggered for every child message in the list when the listener is first attached .THis means
                    // when you attach your listener for every child message that already exists in the db, the code in this method will
                    // will be called.


                    FriendlyMessage message = dataSnapshot.getValue(FriendlyMessage.class);
                    // getValue() deserializes the json format in which firebase stores the data and return an instance of FriendlyMessage
                    // this works because the FriendlyMessage class has the same fields as they are stored in the db.

                    mMessageAdapter.add(message);

                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                    /**
                     * called when contents of an existing msg get changed
                     */
                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {
                    // called when an existing message is removed/deleted
                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                    // when some mdg in our list changes its position in the list

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    // when some error occurs
                }
            };
        }


        messagesDatabseReference.addChildEventListener(childEventListener); //attaching the listener to the database reference
        // which in our case is "messages".

    }
    public void detachDatabseReadListener()
    {
        if(childEventListener!=null) {
            messagesDatabseReference.removeEventListener(childEventListener);
            childEventListener=null;
        }
    }

}
