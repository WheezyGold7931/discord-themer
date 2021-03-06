# discord-themer [![Build Status](https://travis-ci.org/JRoy/discord-themer.svg?branch=master)](https://travis-ci.org/JRoy/discord-themer) [![Release](https://jitpack.io/v/JRoy/discord-themer.svg)](https://jitpack.io/#JRoy/discord-themer) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/8ac7f1e788a3486f815f77a536a15d98)](https://www.codacy.com/app/WheezyGold7931/discord-themer?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=JRoy/discord-themer&amp;utm_campaign=Badge_Grade)
An easy-to-use theme API (Written in Java using [DV8](https://github.com/DV8FromTheWorld)'s [JDA](https://github.com/DV8FromTheWorld/JDA) Discord Wrapper) that allows you to modify multiple aspects of your Discord Server using simple a simple file-based system.

# Getting Started
You will need to add this project as a dependency in a dependency manager of your choice as well as JitPack as a repository.

To link to gradle:
```groovy
dependencies {
    compile 'com.github.JRoy:discord-themer:1.5.0'
}

repositories {
    maven { url 'https://jitpack.io' }
}
```
To use Maven, SBT, or Leiningen check out [JitPack's](https://jitpack.io/#JRoy/discord-themer) site for your respective dependency manger.

# Usage
To create an instance of Discord-Themer, you must go configure the builder [DiscordThemerBuilder](https://github.com/JRoy/discord-themer/blob/master/src/main/java/io/github/wheezygold7931/discordthemer/DiscordThemerBuilder.java) and run DiscordThemerBuilder#build() to get a [DiscordThemer](https://github.com/JRoy/discord-themer/blob/master/src/main/java/io/github/wheezygold7931/discordthemer/DiscordThemer.java) object which will actually do the work/process the themes.

The builder/themer needs to be registered **AFTER** your JDA instance has been loaded as it needs to take in the JDA variable. As this is the case I *strongly* recommend using JDABuilder#buildBlocking() when creating your JDA instance so your JDA object is not null when passing it into the ThemerBuilder (it will throw a IllegalArgumentException if that is the case).

Here is some example code for loading a JDA Instance with Discord-Themer:
```java
public class Bot {

    private static DiscordThemer discordThemer;

    public static void main(String[] args) throws LoginException {

        JDA jda = new JDABuilder(AccountType.BOT)
                .setToken("TOKEN")
                .buildBlocking(); //This makes sure that JDA is fully loaded before we pass it over to discord-themer

        DiscordThemerBuilder builder = new DiscordThemerBuilder(jda);
        discordThemer = builder.setGuild("GUILD ID") //The target guild you want to theme
        .setActionMode(ActionMode.QUEUE) //The method of RestActions to be used (QUEUE and BLOCKING are options)
        .setThemeFolder("themes/") //Directory where the theme files and icons will be stored
        .setLogDisplayWarnings(true) //Toggle if you should get warnings in your console
        .setDebugMode(true) //Toggle if you should get debug output in your console
        .build(); //Converts the DiscordThemerBuilder object into a DiscordThemer object

    }
}
```
To view the complete usage for DiscordThemerBuilder please check out the [wiki](https://github.com/JRoy/discord-themer/wiki) for the full usage.

# Theme File Format
Theme files are text files with the file extension ".dat". Any icon files you use must be valid png images as well.

For each line, the parser splits the current theme file into a string array split at the ':' character. Each one of these splits are called a line token.

Speaking of data, at the moment, theme files can store two different *kinds* of data: MetaData & Role Data.

Here is the MetaData Structure:

> MetaData:META_TYPE:META_VALUE

Now, in the actual theme file, you'll want to replace the "META_VALUE" with the value for the type and replace the "META_TYPE" with one of the following MetaData types:

> name - The display name of the theme (Not the theme id that you will use in the code; That's the file name...).

> title - (Optional) - The title you want the guild to have for the target theme (The name of the server).

> icon - (Optional) - The file name of the png file you'd like as your server icon. (Do not include the .png)

> avatar - (Optional) - The file name of the png file you'd like as your bot's avatar. (Do not include the .png)

> nickname - (Optional) - The nickname of the bot.

> parser - The version of the parser the theme should use. This should be set to 1 as there is only one parser version for now.

While it is completely optional, you can choose to theme roles using the following format:

> ROLE_ID:ROLE_NAME_VALUE

You'll want to replace "ROLE_ID" with the id of the role when you want to theme and the "ROLE_VALUE" should be what you want the role to be changed to.

Here is a visual of how a theme file looks:
```
//This is a comment so this line will not be parsed by the parser!
//Lines that start with / or # will be considered comments!
//Note: Only full line comments are supported.
//The parser will also ignore blank lines!

//Metadata
MetaData:name:Theme Display Name
MetaData:title:Guild Title
MetaData:icon:Icon_File_Name
MetaData:avatar:Avatar_File_Name
MetaData:nickname:Bot Nickname
MetaData:parser:1

//Role Data
r0leid:New Role Name
r0leid:New Role Name
r0leid:New Role Name
r0leid:New Role Name
```
