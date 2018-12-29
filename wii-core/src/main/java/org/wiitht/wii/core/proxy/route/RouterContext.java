package org.wiitht.wii.core.proxy.route;

import org.wiitht.wii.core.internal.manage.ManageContext;

/**
 * @Author wii
 * @Date 18-12-24-下午3:17
 * @Version 1.0
 */
public class RouterContext {

    private ManageContext manageContext;

    public RouterContext(ManageContext context){
        this.manageContext = context;
    }

    public ManageContext getManageContext() {
        return manageContext;
    }

    public void setManageContext(ManageContext manageContext) {
        this.manageContext = manageContext;
    }
}