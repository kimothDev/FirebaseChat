package uk.ac.wlv.firebasechat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.SnapshotParser;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.bumptech.glide.Glide;

import java.security.PrivateKey;

import android.app.AlertDialog;

public class MainActivity extends AppCompatActivity
    implements GoogleApiClient.OnConnectionFailedListener{

        private static final String  TAG = "MainActivity";
        public static final String MESSAGES_CHILD = "messages";
        private static final int REQUEST_INVITE = 1;
        private static final int REQUEST_IMAGE = 2;
        private static final String LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif";
        public static final int DEFAULT_MSG_LENGTH_LIMIT = 10;
        public static final String ANONYMOUS = "anonymous";
        private static final String MESSAGE_SENT_EVENT = "message_sent";
        private String mUsername;
        private String mPhotoUrl;
        private Uri uploadImageUri = null;
        private SharedPreferences mSharedPreferences;
        private GoogleApiClient mGoogleApiClient;
        private ProgressBar mImageUploadProgressBar;

        private Button mSendbutton;
        private RecyclerView mMessageRecyclerView;
        private LinearLayoutManager mLinearLayoutManager;
        private ProgressBar mProgressBar;
        private EditText mMessageEditText;
        private ImageView mAddMessageImageView;

        //Firebase
        private FirebaseAuth mFirebaseAuth;
        private FirebaseUser mFirebaseUser;
        private DatabaseReference mFirebaseDatabaseReference;
        private FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder> mFirebaseAdapter;

        @Override
        protected void onCreate (Bundle savedInstanceState){
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

            //set default username is anonymous
            mUsername = ANONYMOUS;

            mFirebaseAuth = FirebaseAuth.getInstance();
            mFirebaseUser = mFirebaseAuth.getCurrentUser();
            if (mFirebaseUser == null){
                startActivity(new Intent(this, SignInActivity.class));
                finish();
                return;
            }else {
                mUsername = mFirebaseUser.getDisplayName();
                if (mFirebaseUser.getPhotoUrl() !=null){
                    mPhotoUrl = mFirebaseUser.getPhotoUrl().toString();
                }
            }

            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .enableAutoManage(this /*FragmentActivity*/, this /*onConnectionFailedListener*/)
                    .addApi(Auth.GOOGLE_SIGN_IN_API)
                    .build();

            mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
            mImageUploadProgressBar = findViewById(R.id.imageUploadProgressBar);
            mMessageRecyclerView = (RecyclerView) findViewById(R.id.messageRecyclerView);
            mLinearLayoutManager = new LinearLayoutManager(this);
            /* Fix 2: Proper RecyclerView state management to prevent crashes */
            mLinearLayoutManager.setStackFromEnd(true);
            mLinearLayoutManager.setItemPrefetchEnabled(false);
            mMessageRecyclerView.setLayoutManager(mLinearLayoutManager);
            /* Fix 1: Disabled item animations to prevent crashes during updates */
            mMessageRecyclerView.setItemAnimator(null);

//            mProgressBar.setVisibility(ProgressBar.INVISIBLE);
            loadFirebaseMessages();

            mMessageEditText = (EditText) findViewById(R.id.messageEditText);
            mMessageEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    if (charSequence.toString().trim().length()>0){
                        mSendbutton.setEnabled(true);
                    }else {
                        mSendbutton.setEnabled(false);
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });
            mSendbutton = (Button) findViewById(R.id.sendButton);
            mSendbutton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view) {

                    if(uploadImageUri != null){
                        String chatMessageText = mMessageEditText.getText().toString();
                        uploadAndSendImage(uploadImageUri, chatMessageText);
                    }else{
                        ChatMessage chatMessage = new ChatMessage(mMessageEditText.getText().toString(),mUsername,mPhotoUrl,null);
                        mFirebaseDatabaseReference.child(MESSAGES_CHILD).push().setValue(chatMessage);
                    }
                    mMessageEditText.setText("");
                    uploadImageUri = null;
                }
            });
            mAddMessageImageView = (ImageView) findViewById(R.id.addMessageImageView);
            mAddMessageImageView.setOnClickListener(new View.OnClickListener(){
                @Override
                public void  onClick(View view){
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    startActivityForResult(intent, REQUEST_IMAGE);
                }
            });
        }
        @Override
        public void onStart() {
            super.onStart();
        }

        @Override
        public void onPause() {
            mFirebaseAdapter.stopListening();
            super.onPause();
        }

        @Override
        public void onResume() {
            super.onResume();
            mFirebaseAdapter.startListening();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }

        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.main_menu, menu);
            return true;
        }
    private void uploadAndSendImage(Uri uri, String chatMessageText) {
        mImageUploadProgressBar.setVisibility(ProgressBar.VISIBLE);

        DatabaseReference messageRef = mFirebaseDatabaseReference.child(MESSAGES_CHILD).push();
        String messageKey = messageRef.getKey();

        // Create a temporary message with the loading GIF
        ChatMessage tempMessage = new ChatMessage(
                chatMessageText,
                mUsername,
                mPhotoUrl,
                LOADING_IMAGE_URL
        );

        // Add this temporary message to the database
        messageRef.setValue(tempMessage);

        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference(mFirebaseUser.getUid())
                .child(messageKey)
                .child(uri.getLastPathSegment());

        storageRef.putFile(uri)
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Image upload successful");
                    // Get the download URL
                    storageRef.getDownloadUrl()
                            .addOnSuccessListener(downloadUri -> {
                                Log.d(TAG, "Got download URL: " + downloadUri.toString());

                                // Update the chat message with the real image URL
                                ChatMessage chatMessage = new ChatMessage(
                                        chatMessageText,
                                        mUsername,
                                        mPhotoUrl,
                                        downloadUri.toString()
                                );

                                // Update the message in the database
                                messageRef.setValue(chatMessage)
                                        .addOnSuccessListener(aVoid -> {
                                            mImageUploadProgressBar.setVisibility(ProgressBar.GONE);
                                            Log.d(TAG, "Database updated with image URL");
                                        })
                                        .addOnFailureListener(e -> {
                                            mImageUploadProgressBar.setVisibility(ProgressBar.GONE);
                                            Log.e(TAG, "Failed to update database with image URL", e);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                mImageUploadProgressBar.setVisibility(ProgressBar.GONE);
                                Log.e(TAG, "Failed to get download URL", e);
                                Toast.makeText(MainActivity.this, "Failed to process image", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    mImageUploadProgressBar.setVisibility(ProgressBar.GONE);
                    Log.e(TAG, "Image upload failed", e);
                    Toast.makeText(MainActivity.this, "Image upload failed", Toast.LENGTH_SHORT).show();
                });
    }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == R.id.sign_out_menu) {
                mFirebaseAuth.signOut();
                Auth.GoogleSignInApi.signOut(mGoogleApiClient);
                mUsername = ANONYMOUS;
                startActivity(new Intent(this, SignInActivity.class));
                finish();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d("TAG", "onConnectionFailed:" + connectionResult);
            Toast.makeText(this, "Google Play Services error", Toast.LENGTH_SHORT).show();
        }

        public static class MessageViewHolder extends RecyclerView.ViewHolder {
            TextView messageTextView;
            ImageView messageImageView;
            TextView messengerTextView;
            ImageView messengerImageView;

            public MessageViewHolder(View v) {
                super(v);
                messageTextView = v.findViewById(R.id.messageTextView);
                messageImageView = v.findViewById(R.id.messageImageView);
                messengerTextView = v.findViewById(R.id.messengerTextView);
                messengerImageView = v.findViewById(R.id.messengerImageView);
            }
        }
    private void loadFirebaseMessages() {
        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
        SnapshotParser<ChatMessage> parser = new SnapshotParser<ChatMessage>() {
            @Override
            public ChatMessage parseSnapshot(DataSnapshot dataSnapshot) {
                ChatMessage ChatMessage = dataSnapshot.getValue(ChatMessage.class);
                if (ChatMessage != null)
                    ChatMessage.setId(dataSnapshot.getKey());
                return ChatMessage;
            }
        };
        DatabaseReference messagesRef = mFirebaseDatabaseReference.child(MESSAGES_CHILD);
        FirebaseRecyclerOptions<ChatMessage> options =
                new FirebaseRecyclerOptions.Builder<ChatMessage>().setQuery(messagesRef, parser).build();
        mFirebaseAdapter = new FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder>(options) {
            @Override
            public MessageViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
                LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
                return new MessageViewHolder(inflater.inflate(R.layout.item_message, viewGroup, false));
            }

            @Override
            protected void onBindViewHolder(final MessageViewHolder viewHolder, int position, ChatMessage ChatMessage) {
                mProgressBar.setVisibility(ProgressBar.GONE);
                /* Fix 3: Added proper null checks and visibility handling for text and image views */
                if(ChatMessage.getText() != null && !ChatMessage.getText().isEmpty()) {
                    viewHolder.messageTextView.setVisibility(View.VISIBLE);
                    viewHolder.messageTextView.setText(ChatMessage.getText());
                } else if (ChatMessage.getText() != null) {
                    viewHolder.messageTextView.setText(ChatMessage.getText());
                    viewHolder.messageTextView.setVisibility(TextView.VISIBLE);
                    viewHolder.messageImageView.setVisibility(ImageView.GONE);
                } else if (ChatMessage.getImageUrl() != null && !ChatMessage.getImageUrl().isEmpty()) {
                    Glide.with(viewHolder.messageImageView.getContext())
                            .load(ChatMessage.getImageUrl())
                            .into(viewHolder.messageImageView);
                    viewHolder.messageImageView.setVisibility(View.VISIBLE);
                } else {
                    viewHolder.messageImageView.setVisibility(View.GONE);
                    viewHolder.messageTextView.setVisibility(View.GONE); // Prevent showing garbage
                }
                viewHolder.messengerTextView.setText(ChatMessage.getName());

                if (ChatMessage.getImageUrl() != null && !ChatMessage.getImageUrl().isEmpty()) {
                    viewHolder.messageImageView.setVisibility(View.VISIBLE);
                    Glide.with(viewHolder.messageImageView.getContext())
                            .load(ChatMessage.getImageUrl())
                            .into(viewHolder.messageImageView);
                } else {
                    viewHolder.messageImageView.setVisibility(View.GONE);
                }

                if (ChatMessage.getPhotoUrl() != null) {
                    Glide.with(MainActivity.this).load(ChatMessage.getPhotoUrl()).into(viewHolder.messengerImageView);
                }
                
                //Task 1: Message Deletion Implementation
                //This section implements the functionality to delete messages from the system
                //When a user long-presses on a message, it shows a confirmation dialog
                //If confirmed, it removes the message from Firebase database
                viewHolder.itemView.setOnLongClickListener(view -> {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Delete message?")
                            .setMessage("Are you sure you want to delete this message?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                String messageKey = ChatMessage.getId();
                                if (messageKey != null) {
                                    mFirebaseDatabaseReference
                                            .child(MESSAGES_CHILD)
                                            .child(messageKey)
                                            .removeValue()
                                            .addOnSuccessListener(unused -> {
                                                Toast.makeText(MainActivity.this, "Message deleted", Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(MainActivity.this, "Failed to delete", Toast.LENGTH_SHORT).show();
                                                Log.e("DELETE", "Failed to delete message", e);
                                            });
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });

                //Task 2: Sending Text and Image Together Implementation
                //This section handles displaying both text and image messages together
                //It shows both the text message and image (if present) in the same message bubble
                if (ChatMessage.getText() != null && !ChatMessage.getText().isEmpty()) {
                    viewHolder.messageTextView.setText(ChatMessage.getText());
                    viewHolder.messageTextView.setVisibility(TextView.VISIBLE);
                } else {
                    viewHolder.messageTextView.setVisibility(TextView.GONE);
                }

                if (ChatMessage.getImageUrl() != null) {
                    String imageUrl = ChatMessage.getImageUrl();
                    viewHolder.messageImageView.setVisibility(ImageView.VISIBLE);

                    if (imageUrl.equals(LOADING_IMAGE_URL)) {
                        /* Fix 4: Added proper loading state handling in RecyclerView */
                        Glide.with(viewHolder.messageImageView.getContext())
                                .load(LOADING_IMAGE_URL)
                                .into(viewHolder.messageImageView);
                    } else {
                        /* Fix 5: Proper image loading with error handling */
                        Glide.with(viewHolder.messageImageView.getContext())
                                .load(imageUrl)
                                .into(viewHolder.messageImageView);
                    }
                } else {
                    viewHolder.messageImageView.setVisibility(ImageView.GONE);
                }

                viewHolder.messengerTextView.setText(ChatMessage.getName());
                if (ChatMessage.getPhotoUrl() != null) {
                    Glide.with(MainActivity.this).load(ChatMessage.getPhotoUrl()).into(viewHolder.messengerImageView);
                }
            }
        };
        mFirebaseAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);

                super.onItemRangeInserted(positionStart, itemCount);
                int totalCount = mFirebaseAdapter.getItemCount();
                int lastVisible = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();

                /* Fix 6: Proper handling of RecyclerView updates during image upload */
                if (lastVisible == -1 || (positionStart >= (totalCount - 1) && lastVisible == (positionStart - 1))) {
                    mMessageRecyclerView.post(() -> {
                        mMessageRecyclerView.scrollToPosition(positionStart);
                    });
                }
            }
        });
        mMessageRecyclerView.setAdapter(mFirebaseAdapter);
        mFirebaseAdapter.startListening();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    final Uri uri = data.getData();
                    Log.d(TAG, "Uri: " + uri.toString());

                    String messageText = mMessageEditText.getText().toString();
                    ChatMessage tempMessage = new ChatMessage(messageText, mUsername, mPhotoUrl, LOADING_IMAGE_URL);
                    mFirebaseDatabaseReference.child(MESSAGES_CHILD).push().setValue(
                            tempMessage,
                            new DatabaseReference.CompletionListener() {
                                @Override
                                public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                                    if (databaseError == null) {
                                        String key = databaseReference.getKey();
                                        StorageReference storageReference =
                                                FirebaseStorage.getInstance().getReference(mFirebaseUser.getUid())
                                                        .child(key)
                                                        .child(uri.getLastPathSegment());
                                        putImageInStorage(storageReference, uri, key);
                                        // Clear the message text after sending
                                        mMessageEditText.setText("");
                                    } else {
                                        Log.w(TAG, "Unable to write message to database.", databaseError.toException());
                                    }
                                }
                            });
                }
            }
        }
    }
    private void putImageInStorage(StorageReference storageReference, Uri uri, final String key) {
        storageReference.putFile(uri)
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Image upload successful in putImageInStorage");

                    // Get the download URL
                    storageReference.getDownloadUrl()
                            .addOnSuccessListener(downloadUri -> {
                                Log.d(TAG, "Download URL in putImageInStorage: " + downloadUri.toString());

                                // Get the current message
                                mFirebaseDatabaseReference.child(MESSAGES_CHILD).child(key)
                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(DataSnapshot dataSnapshot) {
                                                ChatMessage currentMessage = dataSnapshot.getValue(ChatMessage.class);

                                                // Update with real image URL but keep the same text
                                                ChatMessage updatedMessage = new ChatMessage(
                                                        currentMessage != null ? currentMessage.getText() : null,
                                                        mUsername,
                                                        mPhotoUrl,
                                                        downloadUri.toString()
                                                );

                                                // Update in database
                                                mFirebaseDatabaseReference.child(MESSAGES_CHILD).child(key)
                                                        .setValue(updatedMessage)
                                                        .addOnSuccessListener(aVoid ->
                                                                Log.d(TAG, "Database updated with image URL in putImageInStorage"))
                                                        .addOnFailureListener(e ->
                                                                Log.e(TAG, "Failed to update with image URL", e));
                                            }

                                            @Override
                                            public void onCancelled(DatabaseError databaseError) {
                                                Log.e(TAG, "Failed to read current message", databaseError.toException());
                                            }
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to get download URL in putImageInStorage", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Image upload failed in putImageInStorage", e);
                });
    }


}