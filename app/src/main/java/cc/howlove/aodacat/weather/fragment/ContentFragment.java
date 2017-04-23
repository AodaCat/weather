package cc.howlove.aodacat.weather.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import cc.howlove.aodacat.weather.R;
import cc.howlove.aodacat.weather.activity.MainActivity;
import cc.howlove.aodacat.weather.adapter.WeatherAdapter;
import cc.howlove.aodacat.weather.entity.FutureWeather;
import cc.howlove.aodacat.weather.entity.WeatherDataEntity;
import cc.howlove.aodacat.weather.location.LocationUtil;
import cc.howlove.aodacat.weather.logutil.LogUtil;
import cc.howlove.aodacat.weather.sqlite.WeatherDataUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by anymore on 17-4-22.
 */

public class ContentFragment extends Fragment{
    private static final String tag = "ContentFragment";
    public String cityname;
    private SwipeRefreshLayout srlRefresh;
    private RecyclerView rvWeatherDatas;
    private OkHttpClient mOkHttpClient;
    private Gson mGson;
    private WeatherDataUtil mWeatherDataUtil;
    private MainActivity mMainActivity;
    private List<FutureWeather> futureWeathers;
    private WeatherAdapter mWeatherAdapter;
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            if (srlRefresh.isRefreshing()){
                srlRefresh.setRefreshing(false);
            }
            switch (msg.what){
                case MainActivity.CODE_SUCCESS:
                    mWeatherAdapter.notifyDataSetChanged();
                    break;
                case MainActivity.CODE_FAILED:
                    Toast.makeText(getContext(),"更新失败"+msg.obj,Toast.LENGTH_SHORT).show();
                    mWeatherAdapter.notifyDataSetChanged();
                    break;
                case MainActivity.CODE_NO_DATA:
                    Toast.makeText(getContext(),"拉取信息失败...",Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };
    public static ContentFragment newInstance(String cityname){
        ContentFragment fragment = new ContentFragment();
        Bundle bundle = new Bundle();
        bundle.putString("cityname",cityname);
        fragment.setArguments(bundle);
        return fragment;
    }
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainActivity = (MainActivity) getActivity();
        mOkHttpClient = new OkHttpClient.Builder()
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10,TimeUnit.SECONDS)
                .connectTimeout(10,TimeUnit.SECONDS)
                .build();
        mGson = new Gson();
        mWeatherDataUtil = new WeatherDataUtil(getContext(),"weather.db");
        futureWeathers = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View defaultContentView = inflater.inflate(R.layout.content_layout,container,false);
        srlRefresh = (SwipeRefreshLayout) defaultContentView.findViewById(R.id.srl_refresh);
        rvWeatherDatas = (RecyclerView) defaultContentView.findViewById(R.id.rv_weather_datas);
        Bundle bundle = getArguments();
        cityname = bundle.getString("cityname");
        return defaultContentView;
    }

    @Override
    public void onStart() {
        super.onStart();
        setListeners();
        refreshWeather();
    }
    private void setListeners(){
        srlRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshWeather();
            }
        });
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        mWeatherAdapter = new WeatherAdapter(futureWeathers,getContext());
        rvWeatherDatas.setLayoutManager(layoutManager);
        rvWeatherDatas.setAdapter(mWeatherAdapter);
    }
    private void refreshWeather(){
        String url = MainActivity.baseURL1+"?format=2&dtype=json&cityname="+cityname+"&key="+MainActivity.key;
        final Request request = new Request.Builder()
                .url(url)
                .build();
        Call call = mOkHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Message message = Message.obtain();
                WeatherDataEntity entity = mWeatherDataUtil.getWeatherData("default");
                if (entity == null){
                    message.what = MainActivity.CODE_NO_DATA;
                    mHandler.sendMessage(message);
                    return;
                }
                message.what = MainActivity.CODE_FAILED;
                mWeatherAdapter.setTodayWeather(entity.getResult().getToday());
                futureWeathers.clear();
                futureWeathers.addAll(Arrays.asList(entity.getResult().getFuture()));
                message.obj = entity;
                mHandler.sendMessage(message);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String result = response.body().string();
                WeatherDataEntity entity = mGson.fromJson(result,WeatherDataEntity.class);
                LogUtil.v(tag,entity.getReason());
                Message message = Message.obtain();
                if (entity.getResultcode() != 200){
                    message.what = MainActivity.CODE_FAILED;
                    message.obj = entity.getReason();
                    mHandler.sendMessage(message);
                    return;
                }
                mWeatherAdapter.setTodayWeather(entity.getResult().getToday());
                futureWeathers.clear();
                futureWeathers.addAll(Arrays.asList(entity.getResult().getFuture()));
                LogUtil.v(tag,entity.toString());
                message.what = MainActivity.CODE_SUCCESS;
                message.obj = entity;
                mHandler.sendMessage(message);
                mWeatherDataUtil.addWeatherData(entity,"default");
            }
        });
    }

}