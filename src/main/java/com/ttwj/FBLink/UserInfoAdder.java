package com.ttwj.FBLink;


import javax.persistence.PersistenceException;

public class UserInfoAdder extends FBLink {

    public FBLink plugin;

    public UserInfoAdder(FBLink plugin) {

        this.plugin = plugin;
    }

    public void addUserInfo(UserInfo userInfo) throws DuplicateInfoException {
        UserInfo check =  null;
        try {
            check = plugin.getDatabase().find(UserInfo.class)
                .where()
                .ieq("playerName", userInfo.getPlayerName())
                .ieq("fbID", userInfo.getFbID())
                .findUnique();
        }
        catch (PersistenceException e) {
        }

        if (check != null)
            throw new DuplicateInfoException("UserInfo is already in the database");

        plugin.getDatabase().save(userInfo);

    }

    class DuplicateInfoException extends Exception {
        public DuplicateInfoException(String s) {

        }
    }
}