package com.soomla.profile.social.socialauth;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import com.soomla.profile.auth.AuthCallbacks;
import com.soomla.profile.domain.IProvider;
import com.soomla.profile.domain.UserProfile;
import com.soomla.profile.social.ISocialProvider;
import com.soomla.profile.social.SocialCallbacks;
import com.soomla.SoomlaApp;
import com.soomla.SoomlaUtils;

import org.brickred.socialauth.Contact;
import org.brickred.socialauth.Feed;
import org.brickred.socialauth.Profile;
import org.brickred.socialauth.android.DialogListener;
import org.brickred.socialauth.android.SocialAuthAdapter;
import org.brickred.socialauth.android.SocialAuthError;
import org.brickred.socialauth.android.SocialAuthListener;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by refaelos on 29/05/14.
 */
public abstract class SoomlaSocialAuth implements ISocialProvider {

    public SoomlaSocialAuth() {
    }

    private SocialAuthAdapter getSocialAuthAdapter() {
        SocialAuthAdapter socialAuthAdapter = mSocialAuthAdapters.get(getProvider());
        if (socialAuthAdapter == null) {
            socialAuthAdapter = createSocialAuthAdapter(getProvider());
        }

        return socialAuthAdapter;
    }

    private SocialAuthAdapter createSocialAuthAdapter(IProvider.Provider provider) {
        SocialAuthAdapter socialAuthAdapter = new SocialAuthAdapter(new DialogListener() {
            @Override
            public void onComplete(Bundle bundle) {
                SocialAuthAdapter.Provider saProvider = saProviderFromSAName(bundle.getString(SocialAuthAdapter.PROVIDER));
                SoomlaUtils.LogDebug(TAG, "Login completed for SocialAuth provider: " + saProvider.name());
                if (mLoginListener != null) {
                    mLoginListener.success(providerFromSAProvider(saProvider));
                    mLoginListener = null;
                }
            }

            @Override
            public void onError(SocialAuthError socialAuthError) {
                SoomlaUtils.LogError(TAG, socialAuthError.getMessage());
                if (mLoginListener != null) {
                    mLoginListener.fail(socialAuthError.getMessage());
                    mLoginListener = null;
                }
            }

            @Override
            public void onCancel() {
                SoomlaUtils.LogDebug(TAG, "Login canceled");
                if (mLoginListener != null) {
                    mLoginListener.cancel();
                    mLoginListener = null;
                }
            }

            @Override
            public void onBack() {
                SoomlaUtils.LogDebug(TAG, "Login canceled (back)");
                if (mLoginListener != null) {
                    mLoginListener.cancel();
                    mLoginListener = null;
                }
            }
        });

        mSocialAuthAdapters.put(provider, socialAuthAdapter);

        return socialAuthAdapter;
    }

    @Override
    public void updateStatus(final String status, final SocialCallbacks.SocialActionListener socialActionListener) {
        getSocialAuthAdapter().updateStatus(status, new SocialAuthListener<Integer>() {
            @Override
            public void onExecute(String provider, Integer status) {
                if (isOkHttpStatus(status)) {
                    socialActionListener.success();
                }
                else {
                    socialActionListener.fail("Update status operation failed.  (" + status + ")");
                }
            }

            @Override
            public void onError(SocialAuthError socialAuthError) {
                socialActionListener.fail(socialAuthError.getMessage());
            }
        }, false);
    }

    @Override
    public void updateStory(String message, String name, String caption, String description,
                            String link, String picture,
                            final SocialCallbacks.SocialActionListener socialActionListener) {
        try {
            getSocialAuthAdapter().updateStory(
                    message, name, caption, description, link, picture, new SocialAuthListener<Integer>() {
                @Override
                public void onExecute(String provider, Integer status) {
                    if (isOkHttpStatus(status)) {
                        socialActionListener.success();
                    }
                    else {
                        socialActionListener.fail("Update story operation failed.  (" + status + ")");
                    }
                }

                @Override
                public void onError(SocialAuthError socialAuthError) {
                    socialActionListener.fail(socialAuthError.getMessage());
                }
            });
        } catch (UnsupportedEncodingException e) {
            SoomlaUtils.LogDebug(TAG, e.getMessage());
            socialActionListener.fail(e.getMessage());
        }
    }

    @Override
    public void uploadImage(String message, String fileName, Bitmap bitmap, int jpegQuality,
                            final SocialCallbacks.SocialActionListener socialActionListener) {
        try {
            getSocialAuthAdapter().uploadImageAsync(message, fileName, bitmap, jpegQuality, new SocialAuthListener<Integer>() {
                @Override
                public void onExecute(String provider, Integer status) {
                    if (isOkHttpStatus(status)) {
                        socialActionListener.success();
                    }
                    else {
                        socialActionListener.fail("uploadImage operation failed.  (" + status + ")");
                    }
                }

                @Override
                public void onError(SocialAuthError socialAuthError) {
                    socialActionListener.fail(socialAuthError.getMessage());
                }
            });
        } catch (Exception e) {
            SoomlaUtils.LogDebug(TAG, e.getMessage());
            socialActionListener.fail(e.getMessage());
        }
    }

    @Override
    public void uploadImage(String message, String filePath, final SocialCallbacks.SocialActionListener socialActionListener) {
        try {
            // this is a bit of a hack, the API only supports Bitmap (or InputStream underneath)
            // but it seems that facebook SDK also uses Bitmap on Android
            File file = new File(filePath);
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            getSocialAuthAdapter().uploadImageAsync(message, file.getName(), bitmap, 100, new SocialAuthListener<Integer>() {
                @Override
                public void onExecute(String provider, Integer status) {
                    if (isOkHttpStatus(status)) {
                        socialActionListener.success();
                    } else {
                        socialActionListener.fail("uploadImage operation failed.  (" + status + ")");
                    }
                }

                @Override
                public void onError(SocialAuthError socialAuthError) {
                    socialActionListener.fail(socialAuthError.getMessage());
                }
            });
        } catch (Exception e) {
            socialActionListener.fail(e.getMessage() + ": " + filePath);
        }
    }

    @Override
    public void getContacts(final SocialCallbacks.ContactsListener contactsListener) {
        getSocialAuthAdapter().getContactListAsync(new SocialAuthListener<List<Contact>>() {
            @Override
            public void onExecute(String provider, List<Contact> contacts) {
                if (contacts == null) {
                    SoomlaUtils.LogDebug(TAG, "null contacts? setting to empty");
                    contacts = new ArrayList<Contact>();
                }

                List<UserProfile> contactProfiles = new ArrayList<UserProfile>(contacts.size());
                for (Contact contact : contacts) {
                    UserProfile contactProfile = new UserProfile(getProvider(),
                            contact.getId(), contact.getDisplayName(), contact.getEmail(),
                            contact.getFirstName(), contact.getLastName());
                    contactProfile.setAvatarLink(contact.getProfileImageURL());
                }

                contactsListener.success(contactProfiles);
            }

            @Override
            public void onError(SocialAuthError socialAuthError) {
                contactsListener.fail(socialAuthError.getMessage());
            }
        });
    }

//    @Override
    public void getFeeds(final SocialCallbacks.FeedsListener feedsListener) {
        getSocialAuthAdapter().getFeedsAsync(new SocialAuthListener<List<Feed>>() {
            @Override
            public void onExecute(String provider, List<Feed> saFeeds) {
                if (saFeeds == null) {
                    SoomlaUtils.LogDebug(TAG, "null feeds? setting to empty");
                    saFeeds = new ArrayList<Feed>();
                }

                // todo: model feeds
                List<String> feeds = new ArrayList<String>(saFeeds.size());
                for (Feed feed : saFeeds) {
                    Log.d(TAG, feed.toString());
                    //todo: publish event
                }

                feedsListener.success(feeds);
            }

            @Override
            public void onError(SocialAuthError socialAuthError) {
                feedsListener.fail(socialAuthError.getMessage());
            }
        });
    }

    @Override
    public void login(Activity activity, AuthCallbacks.LoginListener loginListener) {
        mLoginListener = loginListener;
        // Context context = SoomlaApp.getAppContext(); // will crash Dialog
        getSocialAuthAdapter().authorize(activity, saProviderFromProvider(getProvider()));
    }

    @Override
    public void getUserProfile(final AuthCallbacks.UserProfileListener userProfileListener) {
        getSocialAuthAdapter().getUserProfileAsync(new SocialAuthListener<Profile>() {
            @Override
            public void onExecute(String providerName, Profile profile) {
                UserProfile userProfile = new UserProfile(getProvider(), profile.getValidatedId(),
                        profile.getFullName(), profile.getEmail(),
                        profile.getFirstName(), profile.getLastName());
                userProfile.setAvatarLink(profile.getProfileImageURL());


                userProfileListener.success(userProfile);
            }

            @Override
            public void onError(SocialAuthError socialAuthError) {
                userProfileListener.fail(socialAuthError.getMessage());
            }
        });
    }

    @Override
    public void logout(AuthCallbacks.LogoutListener logoutListener) {
        getSocialAuthAdapter().signOut(SoomlaApp.getAppContext(), saProviderFromProvider(getProvider()).name());
        logoutListener.success();
    }


    private SocialAuthAdapter.Provider saProviderFromSAName(String saProviderName) {
        for (SocialAuthAdapter.Provider saProvider : SocialAuthAdapter.Provider.values()) {
            if (saProvider.name().toLowerCase().equals(saProviderName.toLowerCase())) {
                return saProvider;
            }
        }
        return null;
    }

    private SocialAuthAdapter.Provider saProviderFromProvider(IProvider.Provider provider) {
        if (provider == IProvider.Provider.FACEBOOK) {
            return SocialAuthAdapter.Provider.FACEBOOK;
        } else if (provider == IProvider.Provider.FOURSQUARE) {
            return SocialAuthAdapter.Provider.FOURSQUARE;
        } else if (provider == IProvider.Provider.GOOGLE) {
            return SocialAuthAdapter.Provider.GOOGLE;
        } else if (provider == IProvider.Provider.LINKEDIN) {
            return SocialAuthAdapter.Provider.LINKEDIN;
        } else if (provider == IProvider.Provider.MYSPACE) {
            return SocialAuthAdapter.Provider.MYSPACE;
        } else if (provider == IProvider.Provider.TWITTER) {
            return SocialAuthAdapter.Provider.TWITTER;
        } else if (provider == IProvider.Provider.YAHOO) {
            return SocialAuthAdapter.Provider.YAHOO;
        } else if (provider == IProvider.Provider.SALESFORCE) {
            return SocialAuthAdapter.Provider.SALESFORCE;
        } else if (provider == IProvider.Provider.YAMMER) {
            return SocialAuthAdapter.Provider.YAMMER;
        } else if (provider == IProvider.Provider.RUNKEEPER) {
            return SocialAuthAdapter.Provider.RUNKEEPER;
        } else if (provider == IProvider.Provider.INSTAGRAM) {
            return SocialAuthAdapter.Provider.INSTAGRAM;
        } else if (provider == IProvider.Provider.FLICKR) {
            return SocialAuthAdapter.Provider.FLICKR;
        }
        return null;
    }


    private IProvider.Provider providerFromSAProvider(SocialAuthAdapter.Provider saProvider) {
        if (saProvider == SocialAuthAdapter.Provider.FACEBOOK) {
            return IProvider.Provider.FACEBOOK;
        } else if (saProvider == SocialAuthAdapter.Provider.FOURSQUARE) {
            return IProvider.Provider.FOURSQUARE;
        } else if (saProvider == SocialAuthAdapter.Provider.GOOGLE) {
            return IProvider.Provider.GOOGLE;
        } else if (saProvider == SocialAuthAdapter.Provider.LINKEDIN) {
            return IProvider.Provider.LINKEDIN;
        } else if (saProvider == SocialAuthAdapter.Provider.MYSPACE) {
            return IProvider.Provider.MYSPACE;
        } else if (saProvider == SocialAuthAdapter.Provider.TWITTER) {
            return IProvider.Provider.TWITTER;
        } else if (saProvider == SocialAuthAdapter.Provider.YAHOO) {
            return IProvider.Provider.YAHOO;
        } else if (saProvider == SocialAuthAdapter.Provider.SALESFORCE) {
            return IProvider.Provider.SALESFORCE;
        } else if (saProvider == SocialAuthAdapter.Provider.YAMMER) {
            return IProvider.Provider.YAMMER;
        } else if (saProvider == SocialAuthAdapter.Provider.RUNKEEPER) {
            return IProvider.Provider.RUNKEEPER;
        } else if (saProvider == SocialAuthAdapter.Provider.INSTAGRAM) {
            return IProvider.Provider.INSTAGRAM;
        } else if (saProvider == SocialAuthAdapter.Provider.FLICKR) {
            return IProvider.Provider.FLICKR;
        }
        return null;
    }

    private boolean isOkHttpStatus(Integer status) {
        return status == 200 || status == 201 || status == 204;
    }

    private static final String TAG = "SOOMLA SoomlaSocialAuth";

    private Map<Provider, SocialAuthAdapter> mSocialAuthAdapters = new HashMap<Provider, SocialAuthAdapter>();
    private AuthCallbacks.LoginListener mLoginListener;
}
