package com.wj.router_library;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by fuyuxian on 2016/9/22.
 */

public class Router {

    private static Map<String, Configure> sConfigures = new HashMap<>();
    private static Router sRouter;
    private Param mParam;

    private Router() {
    }

    public static void clear() {
        sConfigures.clear();
    }

    public static void put(String url, Class<? extends Activity> clazz, Interceptor... interceptor) throws IllegalArgumentException {
        //url必须以 "/" 结尾
        url = fixUrl(url);
        if (sConfigures.containsKey(url)) {

            throw new IllegalArgumentException("url : " + url + " has bean configured!");

        }

        Configure configure = new Configure();
        configure.url = url;
        configure.clazz = clazz;
        configure.interceptors = interceptor;

        sConfigures.put(url, configure);
    }

    private static String fixUrl(String url) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }

    public static synchronized Router getInstance(Context context) {
        if (sRouter == null) {
            sRouter = new Router();
        }
        sRouter.init(context);

        return sRouter;
    }

    private void init(Context context) {
        mParam = new Param();
        mParam.context = context;
    }

    public Router activity(Activity activity) {
        mParam.activity = activity;
        return this;
    }

    public Router fragment(Fragment fragment) {
        mParam.fragment = fragment;
        return this;
    }

    public Router url(String url) {
        url = fixUrl(url);
        mParam.url = url;
        return this;
    }

    public Router flag(int flag) {
        mParam.flag |= flag;
        return this;
    }

    public Router needResult(boolean needResult) {
        mParam.needResult = needResult;
        return this;
    }

    public Router resultCode(int resultCode) {
        mParam.resultCode = resultCode;
        return this;
    }

    /**
     * 如果url中包含可选参数，则必须通过{@link #addParam(String, String)}来传递参数
     *
     * @param name
     * @param value
     * @return
     */
    public Router addParam(String name, String value) {
        mParam.primitives.put(name, value);
        return this;
    }

    public Router addSerializableParam(String name, Serializable object) {
        mParam.serializations.put(name, object);

        return this;
    }

    public void open() {
        tryToMatch();

        //intercept
        if (intercept()) return;

        Intent intent = getIntent();

        //start activity
        if (mParam.needResult) {
            if (mParam.activity != null) {
                mParam.activity.startActivityForResult(intent, mParam.resultCode);
            } else if (mParam.fragment != null) {
                mParam.fragment.startActivityForResult(intent, mParam.resultCode);
            } else {
                throw new IllegalArgumentException(
                        "activity or fragment must be provided when need result!");
            }

        } else {
            mParam.context.startActivity(intent);
        }

        //防止内存泄漏
        mParam = null;
    }

    protected Intent getIntent() {
        Intent intent = new Intent(mParam.context,
                sConfigures.get(mParam.preconfiguredUrl).clazz);

        //基本类型参数
        for (String name : mParam.primitives.keySet()) {
            if(mParam.primitives.get(name) instanceof String) {
                intent.putExtra(name, (String) mParam.primitives.get(name));
            }
            if(mParam.primitives.get(name) instanceof Boolean){
                intent.putExtra(name,(boolean) mParam.primitives.get(name));
            }
            if(mParam.primitives.get(name) instanceof Integer){
                intent.putExtra(name,(int) mParam.primitives.get(name));
            }
        }

        //序列化参数
        for (String name : mParam.serializations.keySet()) {
            intent.putExtra(name, mParam.serializations.get(name));
        }

        //set flags
        intent.setFlags(mParam.flag);

        return intent;
    }

    private boolean intercept() {
        Interceptor[] interceptors = sConfigures.get(mParam.preconfiguredUrl).interceptors;
        if (interceptors != null) {
            for (Interceptor interceptor : interceptors) {
                if (interceptor.intercept(mParam)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void tryToMatch() {
        String urlPrefix = getUrlPrefix(mParam.url);
        for (String url : sConfigures.keySet()) {
            if (urlPrefix.equals(getUrlPrefix(url))) {
                mParam.preconfiguredUrl = url;
                parseParams();
                return;
            }
        }
        throw new IllegalArgumentException("未找到与url ： " + mParam.url + "相匹配的router");
    }

    private void parseParams() {

        List<String> names1 = new ArrayList<>(); //基本类型参数,必传项
        List<String> names2 = new ArrayList<>(); //基本类型参数，可选项
        List<String> names3 = new ArrayList<>(); //可序列化类型参数,必传项
        List<String> names4 = new ArrayList<>(); //可序列化类型参数，可选项
        List<String> values = new ArrayList<>();

        //必传项
        Pattern primitive1 = Pattern.compile(":([^?+/]+)[+]?[/]");
        Pattern serializable1 = Pattern.compile("@([^?+/]+)[+]?[/]");
        //可选项
        Pattern primitive2 = Pattern.compile(":([^?+/]+)[?][/]");
        Pattern serializable2 = Pattern.compile("@([^?+/]+)[?][/]");

        //获取基本类型的参数,必传项
        capture(names1, mParam.preconfiguredUrl, primitive1);
        //获取基本类型的参数,可选项
        capture(names2, mParam.preconfiguredUrl, primitive2);


        //获取基本类型的参数的值
        capture(values, mParam.url, primitive1);

        //url中包含可选参数时，只能通过addParam来传递参数
        if (!isAllPrimitiveRequired(mParam.preconfiguredUrl) && values.size() > 0) {
            throw new IllegalArgumentException("url : " + mParam.preconfiguredUrl + " 中包含可选参数，只能通过addParam的方式来传递参数");
        }

        //判断是否同时使用了参数两种传递方式
        if (values.size() > 0 && mParam.primitives.size() > 0) {
            throw new IllegalArgumentException("基本参数的传递只能通过url传递或者通过 addParam传递，不能同时使用两种类型！");
        }

        //判断参数的个数是否匹配
        if (isAllPrimitiveRequired(mParam.preconfiguredUrl)) { //只有必传项
            if ((values.size() == 0 && names1.size() != mParam.primitives.size()) ||
                    (mParam.primitives.size() == 0 && names1.size() != values.size())) {
                throw new IllegalArgumentException("基本参数个数不匹配，需要" + names1.size() + "个，实际提供了" + values
                        .size() + "个");
            }
        }

        //通过addParam方式传递参数时，判断参数名是否匹配
        if (mParam.primitives.size() > 0) {
            for (String name : names1) {
                if (!mParam.primitives.containsKey(name)) {
                    throw new IllegalArgumentException("基本参数 " + name + " 未提供");
                }
            }
        }

        //判断参数是否存在
        for (String name : mParam.primitives.keySet()) {
            if (!names1.contains(name) && !names2.contains(name)) {
                throw new IllegalArgumentException("参数 " + name + " 不存在！");
            }
        }

        /**
         * 如果是通过url传递，则将其合并到{@link Param#primitives}中
         */
        if (values.size() > 0) {
            for (int i = 0; i < names1.size(); i++) {
                String name = names1.get(i);
                String value = values.get(i);

                mParam.primitives.put(name, value);
            }
        }


        //获取可序列化类型参数,必传项
        capture(names3, mParam.preconfiguredUrl, serializable1);
        //获取可序列化类型参数,可选项
        capture(names4, mParam.preconfiguredUrl, serializable2);

        for (String name : names3) {
            if (!mParam.serializations.containsKey(name)) {
                throw new IllegalArgumentException("序列化参数" + name + "未提供");
            }
        }

        for (String name : mParam.serializations.keySet()) {
            if (!names3.contains(name) && !names4.contains(name)) {
                throw new IllegalArgumentException("参数 " + name + " 不存在！");
            }
        }

    }

    /**
     * 根据提供的正则捕获url中匹配的值，并存储到给定的list中
     *
     * @param list
     * @param url
     * @param pattern
     */
    private void capture(List<String> list, String url, Pattern pattern) {
        Matcher matcher = pattern.matcher(url);
        while (matcher.find()) {
            String name = matcher.group(1);
            list.add(name);
        }
    }

    /**
     * url中的参数是否全部为必传,只判断基本类型
     *
     * @param url
     * @return
     */
    @VisibleForTesting
    boolean isAllPrimitiveRequired(String url) {
        Pattern pattern = Pattern.compile(".*:[^:@/?]*\\?.*");
        Matcher matcher = pattern.matcher(url);
        return !matcher.matches();
    }


    private String getUrlPrefix(String url) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        if (!url.contains(":")) {
            return url;
        }
        return url.substring(0, url.indexOf(":"));
    }

    public static class Configure {
        /**
         * 拦截器，可以拦截跳转事件，重定向跳转
         */
        Interceptor[] interceptors;
        /**
         * /login/:name/:age* /@user
         * <ul>
         * <li>":" 基本数据类型 string/int/boolean...
         * <li>"@" 可序列化对象 {@link Serializable}
         * <li>"+" 后缀，代表该参数必传，不写是默认为必传
         * <li>"*" 后缀，代表该参数可选
         * </ul>
         */
        String url;
        /**
         * 要跳转到的activity的class
         */
        Class<? extends Activity> clazz;

    }

    public class Param {

        public Context context;

        /**
         * 用于{@link Activity#startActivityForResult(Intent, int)}的方式启动
         */
        public Activity activity;
        /**
         * 用于{@link android.support.v4.app.Fragment#startActivityForResult(Intent, int)}的方式启动
         */
        public Fragment fragment;

        /**
         * 跳转url 包含实际的参数信息
         */
        String url;

        /**
         * 配置文件中定义的url
         */
        String preconfiguredUrl;
        /**
         * for intent
         */
        int flag;
        /**
         * 是否需要返回结果，即是否以{@link Activity#startActivityForResult(Intent, int)}
         * 或{@link android.support.v4.app.Fragment#startActivityForResult(Intent, int)}的方式启动
         */
        boolean needResult;
        /**
         * 返回码
         */
        int resultCode;
        /**
         * 要传递的可序列化对象
         */
        Map<String, Serializable> serializations = new HashMap<>();

        /**
         * 基本数据类型的参数
         */
        Map<String, Object> primitives = new HashMap<>();
    }
}
