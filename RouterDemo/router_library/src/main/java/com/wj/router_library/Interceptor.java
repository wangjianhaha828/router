package com.wj.router_library;


public interface Interceptor {

    /**
     * @return boolean true if intercepted false if not
     */
    boolean intercept(Router.Param param);
}
