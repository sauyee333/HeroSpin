package com.sauyee333.herospin.fragment;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.google.gson.Gson;
import com.sauyee333.herospin.R;
import com.sauyee333.herospin.listener.MainListener;
import com.sauyee333.herospin.network.ProgressSubscriber;
import com.sauyee333.herospin.network.SubscribeOnResponseListener;
import com.sauyee333.herospin.network.marvel.model.characterList.Results;
import com.sauyee333.herospin.network.marvel.model.characterList.Thumbnail;
import com.sauyee333.herospin.network.omdb.model.imdb.ImdbInfo;
import com.sauyee333.herospin.network.omdb.model.searchapi.MovieInfo;
import com.sauyee333.herospin.network.omdb.model.searchapi.SearchInfo;
import com.sauyee333.herospin.network.omdb.rest.OmdbRestClient;
import com.sauyee333.herospin.utils.Constants;
import com.sauyee333.herospin.utils.SysUtility;

import java.lang.ref.WeakReference;
import java.util.Random;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by sauyee on 29/10/16.
 */

public class MovieDetailFragment extends Fragment implements HeroListFragment.AddCharacterListener {

    private static final int MSG_SEARCH_HERO_MOVIE = 100;

    @Bind(R.id.actors)
    TextView actors;

    @Bind(R.id.genre)
    TextView genre;

    @Bind(R.id.language)
    TextView language;

    @Bind(R.id.year)
    TextView year;

    @Bind(R.id.imdb)
    TextView imdb;

    @Bind(R.id.plot)
    TextView plot;

    @Bind(R.id.poster)
    ImageView poster;

    @Bind(R.id.title)
    TextView title;

    @Bind(R.id.heroName)
    TextView heroName;

    @Bind(R.id.heroImage)
    ImageView heroImage;

    @Bind(R.id.movieInfo)
    LinearLayout movieInfo;

    @Bind(R.id.errorInfo)
    LinearLayout errorInfo;

    private final CustomHandler mHandler = new CustomHandler(this);
    private Activity mActivity;
    private Context mContext;
    private MainListener mListener;

    private ImdbInfo mImdbInfo = null;

    private SubscribeOnResponseListener onGetMovieDetailHandler = new SubscribeOnResponseListener<ImdbInfo>() {
        @Override
        public void onNext(ImdbInfo imdbInfo) {
            String response = imdbInfo.getResponse();
            if (!TextUtils.isEmpty(response)) {
                if (response.equals("False")) {
                    displayErrorMessage(imdbInfo.getError());
                } else {
                    mImdbInfo = imdbInfo;
                    updateMovieDetail(imdbInfo);
                }
            }
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
                        displayErrorMessage(movieInfo.getError());
                    } else {
                        SearchInfo[] searchInfos = movieInfo.getSearch();
                        int totalInt = searchInfos.length;
                        if (totalInt > 0) {
                            int random = new Random().nextInt(totalInt);
                            SearchInfo[] searchInfo = movieInfo.getSearch();
                            SearchInfo searchInfo1 = searchInfo[random];
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

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_movie_detail, container, false);
        mActivity = getActivity();
        mContext = getContext();
        ButterKnife.bind(this, view);

        Bundle bundle = this.getArguments();
        if (bundle != null && mImdbInfo == null) {
            String hero = bundle.getString(Constants.BUNDLE_STRING_HERO);
            String imgUrl = bundle.getString(Constants.BUNDLE_STRING_URL);

            if (heroName != null) {
                heroName.setText(hero);
                heroName.setTag(hero);
            }
            String movieInfo = bundle.getString(Constants.BUNDLE_STRING_MOVIE_INFO);
            mImdbInfo = new Gson().fromJson(movieInfo, ImdbInfo.class);
            updateMovieDetail(mImdbInfo);
            loadHeroImage(imgUrl);
        }
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

    @OnClick(R.id.btnUp)
    public void closePage() {
        if (mListener != null) {
            mListener.onFragmentBackPress();
        }
    }

    @OnClick(R.id.btnCharacter)
    public void chooseCharacter() {
        loadHeroListFragment();
    }

    @OnClick(R.id.btnHome)
    public void loadMoviePickFragment() {
        closePage();
    }

    @OnClick(R.id.btnSpin)
    public void spinAgain() {
        if (heroName != null) {
            String hero = (String) heroName.getTag();
            if (!TextUtils.isEmpty(hero)) {
                getMovieList(hero);
            }
        }
    }

    @Override
    public void confirmAddCharacter(Results results) {
        if (results != null) {
            String hero = results.getName();
            if (!TextUtils.isEmpty(hero)) {
                Bundle bundle = new Bundle();
                Thumbnail thumbnail = results.getThumbnail();
                if (thumbnail != null) {
                    String imgUrl = SysUtility.generateImageUrl(thumbnail.getPath(), Constants.MARVEL_IMAGE_STANDARD_MEDIUM, thumbnail.getExtension());
                    bundle.putString(Constants.BUNDLE_STRING_URL, imgUrl);
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

    private void getMovieList(String search) {
        if (!TextUtils.isEmpty(search)) {
            OmdbRestClient.getInstance().getMovieListApi(new ProgressSubscriber<MovieInfo>(onGetMovieListHandler, mContext, true, true),
                    search);
        }
    }

    private void getMovieDetail(String imdbId) {
        if (!TextUtils.isEmpty(imdbId)) {
            OmdbRestClient.getInstance().getMovieDetailApi(new ProgressSubscriber<ImdbInfo>(onGetMovieDetailHandler, mContext, true, true),
                    imdbId);
        }
    }

    private void displayErrorMessage(String msg) {
        if (movieInfo != null) {
            movieInfo.setVisibility(View.GONE);
        }
        if (errorInfo != null) {
            errorInfo.setVisibility(View.VISIBLE);
        }
        loadMovieImage(null);
    }

    private void updateMovieDetail(ImdbInfo imdbInfo) {
        if (imdbInfo != null) {
            actors.setText(imdbInfo.getActors());
            genre.setText(imdbInfo.getGenre());
            language.setText(imdbInfo.getLanguage());
            year.setText(imdbInfo.getYear());
            imdb.setText(imdbInfo.getImdbRating());
            title.setText(imdbInfo.getTitle());

            String plotInfo = imdbInfo.getPlot();
            if (!TextUtils.isEmpty(plotInfo) && !plotInfo.equals("N/A")) {
                plot.setText(plotInfo);
            }
            if (movieInfo != null) {
                movieInfo.setVisibility(View.VISIBLE);
            }
            if (errorInfo != null) {
                errorInfo.setVisibility(View.GONE);
            }
            loadMovieImage(imdbInfo.getPoster());
        }
    }

    private void loadMovieImage(String posterUrl) {
        if (poster != null) {
            if (!TextUtils.isEmpty(posterUrl) && !posterUrl.equals("N/A")) {
                Glide.with(mContext)
                        .load(posterUrl)
                        .error(R.drawable.landscape_medium)
                        .into(poster);
            } else {
                Glide.with(mContext)
                        .load(R.drawable.landscape_medium)
                        .into(poster);
            }
        }
    }

    private void loadHeroImage(String imgUrl) {
        if (heroImage != null) {
            if (!TextUtils.isEmpty(imgUrl) && !imgUrl.equals("N/A")) {
                Glide.with(mContext).load(imgUrl).asBitmap().centerCrop().error(R.drawable.splash_image).into(new BitmapImageViewTarget(heroImage) {
                    @Override
                    protected void setResource(Bitmap resource) {
                        RoundedBitmapDrawable circularBitmapDrawable =
                                RoundedBitmapDrawableFactory.create(mContext.getResources(), resource);
                        circularBitmapDrawable.setCircular(true);
                        heroImage.setImageDrawable(circularBitmapDrawable);
                    }
                });
            } else {
                Glide.with(mContext)
                        .load(R.drawable.splash_image)
                        .into(heroImage);
            }
        }
    }

    private void loadHeroListFragment() {
        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Fragment fragment = new HeroListFragment();
                    fragment.setTargetFragment(MovieDetailFragment.this, 0);
                    mListener.onShowFragment(fragment, false);
                }
            });
        }
    }

    private void handleMessage(Message message) {
        switch (message.what) {
            case MSG_SEARCH_HERO_MOVIE: {
                Bundle bundle = message.getData();
                String hero = bundle.getString(Constants.BUNDLE_STRING_HERO);
                String imgUrl = bundle.getString(Constants.BUNDLE_STRING_URL);

                if (heroName != null) {
                    heroName.setText(hero);
                    heroName.setTag(hero);
                }
                loadHeroImage(imgUrl);
                getMovieList(hero);
            }
            break;
        }
    }

    static class CustomHandler extends Handler {
        WeakReference<MovieDetailFragment> mFrag;

        CustomHandler(MovieDetailFragment aFragment) {
            mFrag = new WeakReference<>(aFragment);
        }

        @Override
        public void handleMessage(Message message) {
            MovieDetailFragment theFrag = mFrag.get();
            if (theFrag != null) {
                theFrag.handleMessage(message);
            }
        }
    }
}
