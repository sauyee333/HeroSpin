package com.sauyee333.herospin.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.sauyee333.herospin.R;
import com.sauyee333.herospin.listener.MainListener;
import com.sauyee333.herospin.network.ProgressSubscriber;
import com.sauyee333.herospin.network.SubscribeOnResponseListener;
import com.sauyee333.herospin.network.marvel.model.characterList.CharacterInfo;
import com.sauyee333.herospin.network.marvel.model.characterList.Data;
import com.sauyee333.herospin.network.marvel.model.characterList.Results;
import com.sauyee333.herospin.network.marvel.model.characterList.Thumbnail;
import com.sauyee333.herospin.network.marvel.rest.MarvelRestClient;
import com.sauyee333.herospin.network.omdb.model.imdb.ImdbInfo;
import com.sauyee333.herospin.network.omdb.model.searchapi.MovieInfo;
import com.sauyee333.herospin.network.omdb.model.searchapi.SearchInfo;
import com.sauyee333.herospin.network.omdb.rest.OmdbRestClient;
import com.sauyee333.herospin.utils.Constants;
import com.sauyee333.herospin.utils.SysUtility;

import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by sauyee on 29/10/16.
 */

public class MoviePickFragment extends Fragment implements HeroListFragment.AddCharacterListener {

    private static final int MSG_SEARCH_HERO_MOVIE = 100;

    @Bind(R.id.spinWheel)
    ImageView spinWheel;

    @Bind(R.id.randomMovieSpin)
    RelativeLayout randomMovieSpin;

    @Bind(R.id.heroName)
    TextView heroName;

    @Bind(R.id.heroImage)
    ImageView heroImage;

    @Bind(R.id.loadingInfo)
    LinearLayout loadingInfo;

    @Bind(R.id.fetchInfo)
    TextView fetchInfo;

    @Bind(R.id.errorLayout)
    LinearLayout errorLayout;

    @Bind(R.id.errorMessage)
    TextView errorMessage;

    private final CustomHandler mHandler = new CustomHandler(this);
    private Activity mActivity;
    private Context mContext;
    private MainListener mListener;
    private Animation animation;

    private int mCharacterOffset = -1;
    private String mHeroImgUrl = "";

    private SubscribeOnResponseListener onGetCharacterListHandler = new SubscribeOnResponseListener<CharacterInfo>() {
        @Override
        public void onNext(CharacterInfo characterInfo) {
            if (characterInfo != null) {
                Data data = characterInfo.getData();
                if (data != null) {

                    Results[] results = data.getResults();
                    if (results != null) {

                        if (mCharacterOffset == -1) {
                            int total = data.getTotal();
                            if (total > 0) {
                                int limit = 1;
                                mCharacterOffset = new Random().nextInt(total);
                                getCharacterList(limit, mCharacterOffset);
                            }
                        } else {
                            int totalInt = results.length;
                            if (totalInt > 0) {
                                Results results1 = results[0];
                                Thumbnail thumbnail = results1.getThumbnail();
                                if (thumbnail != null) {
                                    String imgUrl = SysUtility.generateImageUrl(thumbnail.getPath(), Constants.MARVEL_IMAGE_LANDSCAPE_LARGE, thumbnail.getExtension());
                                    if (heroImage != null && !TextUtils.isEmpty(imgUrl)) {
                                        mHeroImgUrl = imgUrl;
                                        loadHeroImage(imgUrl);
                                    }
                                }
                                String heroSearchStr = results1.getName();
                                if (heroName != null) {
                                    heroName.setText(heroSearchStr);
                                }
                                showLoadingInfo(mContext.getResources().getString(R.string.fetchMovie));
                                if (!TextUtils.isEmpty(heroSearchStr)) {
                                    getMovieList(heroSearchStr);
                                }
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onError(String errorMsg) {
            displayErrorMessage(errorMsg);
        }
    };

    private SubscribeOnResponseListener onGetCharacterIdHandler = new SubscribeOnResponseListener<CharacterInfo>() {
        @Override
        public void onNext(CharacterInfo characterInfo) {
        }

        @Override
        public void onError(String errorMsg) {
            displayErrorMessage(errorMsg);
        }
    };

    private SubscribeOnResponseListener onGetMovieListHandler = new SubscribeOnResponseListener<MovieInfo>() {
        @Override
        public void onNext(MovieInfo movieInfo) {
            if (movieInfo != null) {
                String response = movieInfo.getResponse();
                if (!TextUtils.isEmpty(response)) {
                    if (response.equals("False")) {
                        String msg = movieInfo.getError();
                        if (msg.contains(mContext.getResources().getString(R.string.movieNotFound))) {
                            msg = mContext.getResources().getString(R.string.noMovieTryAgain);
                        }
                        displayErrorMessage(msg);
                    } else {
                        String total = movieInfo.getTotalResults();
                        int totalInt = Integer.parseInt(total);
                        if (totalInt > 0) {
                            SearchInfo[] searchInfo = movieInfo.getSearch();
                            SearchInfo searchInfo1 = searchInfo[0];
                            String imdb = searchInfo1.getImdbID();
                            if (!TextUtils.isEmpty(imdb)) {
                                getMovieDetail(imdb);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onError(String errorMsg) {
            displayErrorMessage(errorMsg);
        }
    };

    private SubscribeOnResponseListener onGetMovieDetailHandler = new SubscribeOnResponseListener<ImdbInfo>() {
        @Override
        public void onNext(ImdbInfo imdbInfo) {
            String response = imdbInfo.getResponse();
            if (!TextUtils.isEmpty(response)) {
                if (response.equals("False")) {
                    displayErrorMessage(imdbInfo.getError());
                } else {
                    Bundle bundle = new Bundle();
                    if (!TextUtils.isEmpty(mHeroImgUrl)) {
                        bundle.putString(Constants.BUNDLE_STRING_URL, mHeroImgUrl);
                    }
                    if (heroName != null) {
                        bundle.putString(Constants.BUNDLE_STRING_HERO, heroName.getText().toString());
                    }
                    bundle.putString(Constants.BUNDLE_STRING_MOVIE_INFO, new Gson().toJson(imdbInfo));
                    loadMovieDetailPage(bundle);
                }
            }
            stopAnim();
            hideLoadingInfo();
        }

        @Override
        public void onError(String errorMsg) {
            displayErrorMessage(errorMsg);
        }
    };

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_movie_pick, container, false);
        ButterKnife.bind(this, view);
        mActivity = getActivity();
        mContext = getContext();
        setupSpinAnimation();
        setupUI();
        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (MainListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement MainListener");
        }
    }

    @OnClick(R.id.randomMovieSpin)
    public void startAnim() {
        randomMovieSpin.setEnabled(false);
        hideErrorInfo();
        startSpinWheel();
        showLoadingInfo(mContext.getResources().getString(R.string.fetchSuperHero));
        resetHeroInfo();
        initGetCharacterTotal();
    }

    public void stopAnim() {
        if (spinWheel != null) {
            spinWheel.clearAnimation();
            spinWheel.animate().cancel();
        }
        hideLoadingInfo();
        randomMovieSpin.setEnabled(true);
    }

    @OnClick(R.id.btnCharacter)
    public void chooseCharacter() {
        loadHeroListFragment();
    }

    @Override
    public void confirmAddCharacter(Results results) {
        if (results != null) {
            String hero = results.getName();
            if (!TextUtils.isEmpty(hero)) {
                Bundle bundle = new Bundle();
                Thumbnail thumbnail = results.getThumbnail();
                if (thumbnail != null) {
                    String imgUrl = SysUtility.generateImageUrl(thumbnail.getPath(), Constants.MARVEL_IMAGE_LANDSCAPE_LARGE, thumbnail.getExtension());
                    bundle.putString(Constants.BUNDLE_STRING_URL, imgUrl);
                    mHeroImgUrl = imgUrl;
                }

                bundle.putString(Constants.BUNDLE_STRING_HERO, hero);
                sendMessageWithBundle(MSG_SEARCH_HERO_MOVIE, bundle);
            }
        }
    }

    private void sendMessageWithBundle(int msgID, Bundle bundle) {
        Message msg = new Message();
        msg.what = msgID;
        if (bundle != null) {
            msg.setData(bundle);
        }
        mHandler.sendMessage(msg);
    }

    private void setupUI() {
        showLoadingInfo(mContext.getResources().getString(R.string.fetchIntro));
    }

    private void setupSpinAnimation() {
        animation = AnimationUtils.loadAnimation(mContext, R.anim.anim_rotate);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
    }

    private void loadMovieDetailPage(Bundle bundle) {
        Fragment fragment = new MovieDetailFragment();
        fragment.setArguments(bundle);
        mListener.onShowFragment(fragment, false);
    }

    private void resetCharacterInfo() {
        mCharacterOffset = -1;
    }

    private void initGetCharacterTotal() {
        int limit = 1;
        int offset = 0;
        resetCharacterInfo();
        //get one item just to get the total character count
        getCharacterList(limit, offset);
    }

    private void getCharacterList(int limit, int offset) {
        String apiKey = getResources().getString(R.string.marvelPublicKey);
        String timeStamp = getTimeStamp();
        String hash = generateHash(timeStamp, getResources().getString(R.string.marvelPrivateKey), apiKey);
        String modified = Constants.MARVEL_QUERY_ORDER_MODIFIED;
        String limitStr = (limit <= 0) ? null : Integer.toString(limit);
        String offsetStr = (limit <= 0 && offset <= 0) ? null : Integer.toString(offset);
        String modifiedSince = Constants.MARVEL_QUERY_MODIFIED_SINCE_DATE;
        MarvelRestClient.getInstance().getCharacterListApi(new ProgressSubscriber<CharacterInfo>(onGetCharacterListHandler, mContext, false, false),
                apiKey, timeStamp, hash,
                null, null, modified,
                limitStr, offsetStr, modifiedSince);
    }

    private void getCharacterId(String id) {
        String characterId = id;
        String apiKey = getResources().getString(R.string.marvelPublicKey);
        String timeStamp = getTimeStamp();
        String hash = generateHash(timeStamp, getResources().getString(R.string.marvelPrivateKey), apiKey);
        MarvelRestClient.getInstance().getCharacterIdApi(new ProgressSubscriber<CharacterInfo>(onGetCharacterIdHandler, mContext, true, true),
                characterId,
                apiKey,
                timeStamp,
                hash);
    }

    private void displayErrorMessage(String msg) {
        stopAnim();
        hideLoadingInfo();
        showErrorInfo(msg);
    }

    private String getTimeStamp() {
        Long tsLong = System.currentTimeMillis();
        return tsLong.toString();
    }

    private String generateMd5(String s) {
        final String MD5 = "MD5";
        try {
            MessageDigest digest = java.security.MessageDigest
                    .getInstance(MD5);
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                String h = Integer.toHexString(0xFF & aMessageDigest);
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String generateHash(String timeStamp, String privateKey, String publicKey) {
        String hash = "";
        if (!TextUtils.isEmpty(timeStamp) && !TextUtils.isEmpty(privateKey) && !TextUtils.isEmpty(publicKey)) {
            hash = generateMd5(timeStamp + privateKey + publicKey);
        }
        return hash;
    }

    private void getMovieList(String search) {
        if (!TextUtils.isEmpty(search)) {
            OmdbRestClient.getInstance().getMovieListApi(new ProgressSubscriber<MovieInfo>(onGetMovieListHandler, mContext, false, false),
                    search);
        }
    }

    private void getMovieDetail(String imdbId) {
        if (!TextUtils.isEmpty(imdbId)) {
            OmdbRestClient.getInstance().getMovieDetailApi(new ProgressSubscriber<ImdbInfo>(onGetMovieDetailHandler, mContext, false, false),
                    imdbId);
        }
    }

    private void loadHeroListFragment() {
        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Fragment fragment = new HeroListFragment();
                    fragment.setTargetFragment(MoviePickFragment.this, 0);
                    mListener.onShowFragment(fragment, false);
                }
            });
        }
    }

    private void startSpinWheel() {
        if (spinWheel != null) {
            spinWheel.startAnimation(animation);
        }
    }

    private void resetHeroInfo() {
        if (heroImage != null) {
            heroImage.setImageResource(android.R.color.transparent);
        }
        if (heroName != null) {
            heroName.setText("");
        }
    }

    private void showLoadingInfo(String input) {
        if (!TextUtils.isEmpty(input)) {
            fetchInfo.setText(input);
        }
        if (loadingInfo != null) {
            loadingInfo.setVisibility(View.VISIBLE);
        }
    }

    private void hideLoadingInfo() {
        if (loadingInfo != null) {
            loadingInfo.setVisibility(View.GONE);
        }
    }

    private void showErrorInfo(String msg) {
        if (!TextUtils.isEmpty(msg) && errorMessage != null) {
            errorMessage.setText(msg);
            if (errorLayout != null) {
                errorLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    private void hideErrorInfo() {
        if (errorLayout != null) {
            errorLayout.setVisibility(View.GONE);
        }
    }

    private void handleMessage(Message message) {
        switch (message.what) {
            case MSG_SEARCH_HERO_MOVIE: {
                Bundle bundle = message.getData();
                String hero = bundle.getString(Constants.BUNDLE_STRING_HERO);
                String imgUrl = bundle.getString(Constants.BUNDLE_STRING_URL);

                if (randomMovieSpin != null) {
                    randomMovieSpin.setEnabled(false);
                }
                if (heroName != null) {
                    heroName.setText(hero);
                }
                loadHeroImage(imgUrl);
                hideErrorInfo();
                startSpinWheel();
                showLoadingInfo(mContext.getResources().getString(R.string.fetchMovie));
                getMovieList(hero);
            }
            break;
        }
    }

    private void loadHeroImage(String imgUrl) {
        if (heroImage != null) {
            if (!TextUtils.isEmpty(imgUrl) && !imgUrl.equals("N/A")) {
                Glide.with(mContext)
                        .load(imgUrl)
                        .error(R.drawable.landscape_medium)
                        .into(heroImage);
            } else {
                Glide.with(mContext)
                        .load(R.drawable.landscape_medium)
                        .into(heroImage);
            }
        }
    }

    static class CustomHandler extends Handler {
        WeakReference<MoviePickFragment> mFrag;

        CustomHandler(MoviePickFragment aFragment) {
            mFrag = new WeakReference<>(aFragment);
        }

        @Override
        public void handleMessage(Message message) {
            MoviePickFragment theFrag = mFrag.get();
            if (theFrag != null) {
                theFrag.handleMessage(message);
            }
        }
    }
}
