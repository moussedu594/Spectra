/*
 * Copyright 2016 John Grosh (jagrosh).
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
package spectra.datasources;

import java.util.ArrayList;
import net.dv8tion.jda.entities.Guild;
import spectra.DataSource;
import spectra.SpConst;

/**
 *
 * @author John Grosh (jagrosh)
 */
public class Overrides extends DataSource{
    private static final Overrides overrides = new Overrides();
    
    private Overrides()
    {
        filename = "discordbot.overrides";
        size = 3;
    }
    
    public static Overrides getInstance()
    {
        return overrides;
    }

    @Override
    protected String generateKey(String[] item) {
        return item[OWNERID]+"|"+item[TAGNAME].toLowerCase();
    }
    
    
    public String[] findTag(Guild guild, String name, boolean nsfw)
    {
        synchronized(data)
        {
            String[] tag = data.get("g"+guild.getId()+"|"+name.toLowerCase());
            if(tag==null)
                return null;
            if(!nsfw && Tags.isNSFW(tag))
                return new String[]{tag[OWNERID],tag[TAGNAME],SpConst.WARNING+"This tag has been marked as **Not Safe For Work** and is not available in this channel."};
            return tag.clone();
        }
    }
    
    public ArrayList<String[]> findGuildTags(Guild guild, boolean nsfw)
    {
        ArrayList<String[]> results = new ArrayList<>();
        synchronized(data)
        {
            data.values().stream().filter((tag) -> (tag[OWNERID].equals("g"+guild.getId()) && (nsfw || !Tags.isNSFW(tag))))
                .forEach((tag) -> {
                results.add(tag.clone());
            });
        }
        return results;
    }
    
    final public static int OWNERID   = 0;
    final public static int TAGNAME   = 1;
    final public static int CONTENTS  = 2;
}
