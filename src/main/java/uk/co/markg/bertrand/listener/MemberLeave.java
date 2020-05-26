package uk.co.markg.bertrand.listener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import uk.co.markg.bertrand.database.UserRepository;

public class MemberLeave extends ListenerAdapter {

  private static final Logger logger = LogManager.getLogger(MemberLeave.class);

  private UserRepository userRepo;

  public MemberLeave() {
    this.userRepo = UserRepository.getRepository();
  }

  @Override
  public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
    long userid = event.getUser().getIdLong();
    if (userRepo.isUserOptedIn(userid)) {
      logger.info("User {} left the server.", userid);
      userRepo.delete(userid);
    }
  }

}
