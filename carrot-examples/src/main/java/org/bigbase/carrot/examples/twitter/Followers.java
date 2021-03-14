package org.bigbase.carrot.examples.twitter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * Class Followers - represents some user's followers
 * @author vrodionov
 *
 */
public class Followers extends Users {

  Followers(User user) {
    super(user);
    //*DEBUG*/System.out.println("followers=" + user.getFollowers() + " size="+ users.size());
  }

  @Override
  public String getKey() {
    return "followers:" + user.getId();
  }

  @Override
  /**
   * Generate followers for a given user
   * @param user user to follow
   * @return user's followers
   */
  public void generateUsers() {
    users = new ArrayList<GenuineUser>();

    long registered = Long.valueOf(user.getSignup());
    int numFollowers = user.getFollowers();
    if (numFollowers == 0) return;
    
    Calendar cal = Calendar.getInstance();
    Date today = cal.getTime();
    cal.setTimeInMillis(registered);
    Date regtime = cal.getTime();
    long interval = (today.getTime() - registered) / numFollowers;
    int count = 0;
    while(count++ < numFollowers) {
      long time = regtime.getTime();
      String id = Long.toString(Id.nextId(time));
      users.add(new GenuineUser(id, time / 1000)); // We keep seconds only
      regtime.setTime (time + interval);
    }
  }

}