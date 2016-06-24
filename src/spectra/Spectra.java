/*
 * Copyright 2016 jagrosh.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spectra;

import javax.security.auth.login.LoginException;
import net.dv8tion.jda.JDABuilder;
import net.dv8tion.jda.Permission;
import net.dv8tion.jda.entities.Role;
import net.dv8tion.jda.events.ReadyEvent;
import net.dv8tion.jda.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.events.message.MessageDeleteEvent;
import net.dv8tion.jda.events.message.MessageReceivedEvent;
import net.dv8tion.jda.events.message.MessageUpdateEvent;
import net.dv8tion.jda.events.user.UserNameUpdateEvent;
import net.dv8tion.jda.hooks.ListenerAdapter;
import net.dv8tion.jda.utils.PermissionUtil;
import spectra.commands.*;
import spectra.datasources.*;
import spectra.tempdata.CallDepend;
import spectra.utils.OtherUtil;
import spectra.utils.FormatUtil;

/**
 *
 * @author John Grosh (jagrosh)
 */
public class Spectra extends ListenerAdapter {
    
    Command[] commands;
    //DataSource[] sources;
    //final Settings settings;

    @Override
    public void onReady(ReadyEvent event) {
        event.getJDA().getAccountManager().setGame("Type "+SpConst.PREFIX+"help");
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        
        PermLevel perm;
        boolean ignore;
        boolean isCommand;
        boolean successful;
        
        //get the settings for the server
        //settings will be null for private messages
        //make default settings if no settings exist for a server
        String[] currentSettings = (event.isPrivate() ? null : Settings.getInstance().getSettingsForGuild(event.getGuild().getId()));
        if(currentSettings==null && !event.isPrivate())
            currentSettings = Settings.getInstance().makeNewSettingsForGuild(event.getGuild().getId());
        
        //get a sorted list of prefixes
        String[] prefixes = event.isPrivate() ?
            new String[]{SpConst.PREFIX,SpConst.ALTPREFIX} :
            Settings.prefixesFromList(currentSettings[Settings.PREFIXES]);
        
        //compare against each prefix
        String strippedMessage=null;
        for(int i=prefixes.length-1;i>=0;i--)
        {
            if(event.getMessage().getRawContent().toLowerCase().startsWith(prefixes[i].toLowerCase()))
            {
                strippedMessage = event.getMessage().getRawContent().substring(prefixes[i].length()).trim();
                break; 
            }
        }
        //find permission level
        perm = PermLevel.EVERYONE;//start with everyone
        if(event.getAuthor().getId().equals(SpConst.JAGROSH_ID))
            perm = PermLevel.JAGROSH;
        else if(!event.isPrivate())//we're in a guild
        {
            if(PermissionUtil.checkPermission(event.getAuthor(), Permission.MANAGE_SERVER, event.getGuild()))
                perm = PermLevel.ADMIN;
            else
            {
                if(currentSettings[Settings.MODIDS].contains(event.getAuthor().getId()))
                    perm = PermLevel.MODERATOR;
                else
                {
                    for(Role r: event.getGuild().getRolesForUser(event.getAuthor()))
                        if(currentSettings[Settings.MODIDS].contains("r"+r.getId()))
                        {
                            perm = PermLevel.MODERATOR;
                            break;
                        }
                }
            }
        }
        
        //check if should ignore
        ignore = false;
        if(!event.isPrivate())
        {
            if( currentSettings[Settings.IGNORELIST].contains("u"+event.getAuthor().getId()) || currentSettings[Settings.IGNORELIST].contains("c"+event.getTextChannel().getId()) )
                ignore = true;
            else if(currentSettings[Settings.IGNORELIST].contains("r"+event.getGuild().getId()) && event.getGuild().getRolesForUser(event.getAuthor()).isEmpty())
                ignore = true;
            else
                for(Role r: event.getGuild().getRolesForUser(event.getAuthor()))
                    if(currentSettings[Settings.IGNORELIST].contains("r"+r.getId()))
                    {
                        ignore = true;
                        break;
                    }
        }
        
        if(strippedMessage!=null)//potential command right here
        {
            strippedMessage = strippedMessage.trim();
            if(strippedMessage.equalsIgnoreCase("help"))//send full help message (based on access level)
            {//we don't worry about ignores for help
                isCommand = true;
                successful = true;
                String helpmsg = "**Available help "+(event.isPrivate() ? "via Direct Message" : "in <#"+event.getTextChannel().getId()+">")+"**:";
                for(Command com: commands)
                {
                    if( perm.isAtLeast(com.level) )
                        helpmsg += "\n`"+SpConst.PREFIX+com.command+"`"+Argument.arrayToString(com.arguments)+" - "+com.help;
                }
                helpmsg+="\n\nFor more information, call "+SpConst.PREFIX+"<command> help. For example, `"+SpConst.PREFIX+"tag help`";
                helpmsg+="\nFor commands, `<argument>` refers to a required argument, while `[argument]` is optional";
                helpmsg+="\nDo not add <> or [] to your arguments, nor quotation marks";
                helpmsg+="\nFor more help, contact **@jagrosh** (<@"+SpConst.JAGROSH_ID+">) or join "+SpConst.JAGZONE_INVITE;
                Sender.sendHelp(helpmsg, event.getAuthor().getPrivateChannel(), event.getTextChannel(), event.getMessage().getId());
            }
            else//wasn't base help command
            {
                Command toRun = null;
                String[] args = FormatUtil.cleanSplit(strippedMessage);
                if(args[0].equalsIgnoreCase("help"))
                {
                    String endhelp = args[1]+" "+args[0];
                    args = FormatUtil.cleanSplit(endhelp);
                }
                for(Command com: commands)
                    if(com.isCommandFor(args[0]))
                    {
                        toRun = com;
                        break;
                    }
                if(toRun!=null)
                {
                    isCommand = true;
                    //check if banned
                    boolean banned = false;
                    if(!event.isPrivate())
                    {
                        for(String bannedCmd : Settings.restrCmdsFromList(currentSettings[Settings.BANNEDCMDS]))
                            if(bannedCmd.equalsIgnoreCase(toRun.command))
                                banned = true;
                        if(banned)
                            if(event.getTextChannel().getTopic().contains("{"+toRun.command+"}"))
                                banned = false;
                    }
                    successful = toRun.run(args[1], event, perm, ignore, banned);
                }
                else if (!event.isPrivate() && (!ignore || perm.isAtLeast(PermLevel.ADMIN)))
                {
                    String[] tagCommands = Settings.tagCommandsFromList(currentSettings[Settings.TAGIMPORTS]);
                    for(String cmd : tagCommands)
                        if(cmd.equalsIgnoreCase(args[0]))
                        {
                            isCommand=true;
                            boolean nsfw = event.getTextChannel().getName().contains("nsfw") || event.getTextChannel().getTopic().toLowerCase().contains("{nsfw}");
                            String[] tag = Overrides.getInstance().findTag(event.getGuild(), cmd, nsfw);
                            if(tag==null)
                                tag = Tags.getInstance().findTag(cmd, null, false, nsfw);
                            if(tag==null)
                            {
                                Sender.sendResponse(SpConst.ERROR+"Tag \""+cmd+"\" no longer exists!", event.getChannel(), event.getMessage().getId());
                            }
                            else
                            {
                                Sender.sendResponse("\u180E"+JagTag.convertText(tag[Tags.CONTENTS], args[1], event.getAuthor(), event.getGuild(), event.getChannel()), 
                                    event.getChannel(), event.getMessage().getId());
                            }
                        }
                }
            }
        }
        
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        
    }

    
    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        CallDepend.getInstance().delete(event.getMessageId());
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        event.getMessageIds().stream().forEach((id) -> {
            CallDepend.getInstance().delete(id);
        });
    }

    
    @Override
    public void onUserNameUpdate(UserNameUpdateEvent event) {
        SavedNames.getInstance().addName(event.getUser().getId(), event.getPreviousUsername());
    }
    
    
    
    
    public Spectra()
    {
        
    }
    
    public void init()
    {
        commands = new Command[]{
            new About(),
            new Archive(),
            new Avatar(),
            new Channel(),
            new Draw(),
            new Info(),
            new Names(),
            new Ping(),
            new Server(),
            new Tag(),
            
            new BotScan()
        };
        
        Overrides.getInstance().read();
        SavedNames.getInstance().read();
        Settings.getInstance().read();
        Tags.getInstance().read();
        
        
        try {
            new JDABuilder().addListener(this).setBotToken(OtherUtil.readFileLines("discordbot.login").get(1)).setBulkDeleteSplittingEnabled(false).buildAsync();
        } catch (LoginException | IllegalArgumentException ex) {
            System.err.println("ERROR - Building JDA : "+ex.toString());
            System.exit(1);
        }
    }
    
    public static void main(String[] args)
    {
        new Spectra().init();
    }
}