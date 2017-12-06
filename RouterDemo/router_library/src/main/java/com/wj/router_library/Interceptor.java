package com.wj.router_library;

/**
 * Created by fuyuxian on 2016/9/22.
 */

public interface Interceptor {

    /**
     * @return boolean true if intercepted false if not
     */
    boolean intercept(Router.Param param);
}
