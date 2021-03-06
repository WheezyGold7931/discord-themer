package io.github.wheezygold7931.discordthemer;

import io.github.wheezygold7931.discordthemer.exceptions.ThemeNotFoundException;
import io.github.wheezygold7931.discordthemer.util.ActionMode;
import io.github.wheezygold7931.discordthemer.util.DiscordThemerLogger;
import io.github.wheezygold7931.discordthemer.util.ParserVersion;
import io.github.wheezygold7931.discordthemer.util.RunRestAction;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Role;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

@SuppressWarnings("WeakerAccess")
public class DiscordThemer {

    private final JDA jda;
    private final Guild guild;
    private final File themeDir;
    private final ActionMode actionMode;
    private final DiscordThemerLogger logger;

    private HashMap<String, ThemeToken> themeMap = new HashMap<>();

    /**
     * Protected Constructor to be used internally only.
     */
    protected DiscordThemer(JDA jda, Guild guild, File themeDir, ActionMode actionMode, DiscordThemerLogger discordThemerLogger) {
        this.jda = jda;
        this.guild = guild;
        this.themeDir = themeDir;
        this.actionMode = actionMode;
        this.logger = discordThemerLogger;
        logger.info("Discord-Themer Initialized!");
        processThemes();
    }

    /**
     * Runs through processing all the files in the theme directory.
     */
    private void processThemes() {
        File[] rawThemes = themeDir.listFiles();

        logger.info("Loading and Parsing Themes...");

        if (rawThemes == null || rawThemes.length == 0) {
            logger.error("No themes are in the theme directory!");
            return;
        }

        for (File theme : rawThemes) {
            if (!theme.isDirectory() && theme.getName().endsWith(".dat")) {
                logger.debug("Sending file to parser: " + theme.getName());
                if (validateTheme(theme)) {
                    parseTheme(theme);
                } else {
                    logger.pdebug("Theme failed validation!", theme.getName());
                }
            }
        }

        logger.info("Loaded and Parsed a total of " + themeMap.size() + " themes!");
        logger.debug("Loaded Themes:");
        for (HashMap.Entry<String, ThemeToken> entry : themeMap.entrySet()) {
            ThemeToken token = entry.getValue();
            logger.debug("    - " + token.getThemeName() + " (" + token.getThemeDisplayName() + ") (Parser: " + token.getParserVersion().getVersionString() + ")");
        }
    }

    /**
     *
     * The parser is basically blind, it does not know if the right amount of tokens per line, etc.
     * This method makes sure the parser will not throw an {@link IndexOutOfBoundsException} or causes other issues as the parser will break if a PERFECT theme file is not given to it.
     *
     * @param file The theme file in question.
     * @return Returns true if the theme file is okay for the parser.
     */
    private boolean validateTheme(File file) {
        String fileName = file.getName(); //Has file extension
        String filePath = file.getPath();

        Scanner validateScanner;
        try {
            validateScanner = new Scanner(file);
        } catch (FileNotFoundException e) {
            logger.perror("File Mismatch! Did the GC steal the file?", fileName);
            return false;
        }

        HashMap<String, String> metaTokens = new HashMap<>();
        List<String> roleIds = new ArrayList<>();
        while (validateScanner.hasNextLine()) {
            String curLine = validateScanner.nextLine();

            if (curLine.startsWith("/") || curLine.startsWith("#"))
                continue; // Ignore comments as they will not be used

            if (curLine.isEmpty())
                continue; //User has decided they like increased parsed times...

            String[] lineTokens = curLine.split("[:]");
            if (lineTokens[0].equalsIgnoreCase("MetaData")) {
                if (lineTokens.length != 3) {
                    logger.perror("Unparseable line: " + curLine, fileName);
                    return false;
                }
                metaTokens.put(lineTokens[1], lineTokens[2]);
            } else {
                if (lineTokens.length != 2) {
                    logger.perror("Unparseable line: " + curLine, fileName);
                    return false;
                }
                if (guild.getRoleById(lineTokens[0]) == null) {
                    logger.pwarn("Invalid Role ID: " + lineTokens[0] + "! This will not be parsed.", fileName);
                    continue;
                }
                if (roleIds.contains(lineTokens[0])) logger.pwarn("Role ID Duplication Detected! Please only use a role once within a theme file!", fileName);
                roleIds.add(lineTokens[0]);
            }
        }

        if (roleIds.size()==0) logger.pwarn("There were no valid roles detected! The server will only be themed with MetaData.", fileName);

        if (!metaTokens.containsKey("name")) {
            logger.perror("Theme name not provided in metadata!", fileName);
            return false;
        }

        if (!metaTokens.containsKey("parser")) {
            logger.pwarn("Parser Version MetaData no provided, using newest parser.", fileName);
        } else {
            if (!ParserVersion.isVersion(metaTokens.get("parser"))) logger.pwarn("Invalid Parser Version! The parser version will be defaulted to the newest one!", fileName);
        }

        if (metaTokens.containsKey("icon")) {
            File image;
            try {
                image = new File(filePath.substring(0, filePath.lastIndexOf('\\')) + "\\" + metaTokens.get("icon") + ".png");
            } catch (StringIndexOutOfBoundsException e) {
                image = new File(themeDir + metaTokens.get("icon") + ".png");
            }

            if (!image.exists() || image.isDirectory()) {
                logger.perror("Invalid Server Image File: " + image.getPath(), fileName);
                logger.perror(" ^ If you were trying to specify another directory, start the metadata value with a slash!", fileName);
                return false;
            }
        }

        if (metaTokens.containsKey("avatar")) {
            File avatar;
            try {
                avatar = new File(filePath.substring(0, filePath.lastIndexOf('\\')) + "\\" + metaTokens.get("avatar") + ".png");
            } catch (StringIndexOutOfBoundsException e) {
                avatar = new File(themeDir + metaTokens.get("avatar") + ".png");
            }
            if (!avatar.exists() || avatar.isDirectory()) {
                logger.perror("Invalid Avatar Image File: " + avatar.getPath(), fileName);
                logger.perror(" ^ If you were trying to specify another directory, start the metadata value with a slash!", fileName);
                return false;
            }
        }
        logger.pdebug("Theme Validated!", fileName);
        return true;
    }

    /**
     * The parser reads a theme file from the theme validator and parses it into a {@link ThemeToken}
     * Note: The parser is extremely stupid, the validator is where the smarts are at. I cannot stress this enough if you're modifying this, run your files through {@link DiscordThemer#validateTheme(File)} FIRST! This method WILL hard fail!
     * @param file The (pre-validated) theme file to be parsed.
     */
    private void parseTheme(File file) {
        String fileName = file.getName();
        String themeName = file.getName().substring(0, file.getName().lastIndexOf('.')); //Remove the file extension from the file

        logger.pdebug("Parsing Theme " + file.getName() + "!", fileName);

        ThemeToken token = new ThemeToken(themeName);

        Scanner dataScanner;
        try {
            dataScanner = new Scanner(file);
        } catch (FileNotFoundException e) {
            logger.perror("File Mismatch! Did the GC steal the file?", fileName);
            return;
        }

        while (dataScanner.hasNextLine()) {
            String curLine = dataScanner.nextLine();
            String[] lineTokens = curLine.split("[:]");

            if (curLine.startsWith("/") || curLine.startsWith("#"))
                continue; //Do not parse comments (Slash, Double-Slash, Hash-Sign)

            if (curLine.isEmpty())
                continue; //User has decided they like increased parsed times...

            if (lineTokens[0].equalsIgnoreCase("MetaData")) {
                token.addMetaData(lineTokens[1], lineTokens[2]);
            } else {
                if (guild.getRoleById(lineTokens[0]) == null) {
                    logger.perror("Unparseable Role: " + curLine + " (Invalid Role ID)", fileName);
                    continue; //Invalid role ids will cause exceptions from JDA.
                }
                token.addData(lineTokens[0], lineTokens[1]);
            }
        }

        themeMap.put(themeName, token.finalizeToken());

    }

    /**
     * Gets the current state of the discord server and export it as a theme file.
     * @param name The name you want the exported theme file.
     * @param parse If the exported theme should be parsed and added to the theme map.
     * @throws IllegalArgumentException Throws if file with name exists.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void captureServer(String name, boolean parse) throws IllegalArgumentException {
        logger.info("Exporting current server state...");
        File file = new File(themeDir.getPath() + "\\" + name + ".dat");
        try {
            if (file.exists())
                throw new IllegalArgumentException("Theme file already exists!");
            file.createNewFile();
            PrintWriter writer = new PrintWriter(file, "UTF-8");
            writer.println("//Theme Auto-Exported from DiscordThemer#captureServer");

            //Theme Display Name
            writer.println("MetaData:name:" + name + " (Auto-Exported)");

            //Guild Name
            writer.println("MetaData:title:" + guild.getName());

            //Convert image to png
            logger.debug("Starting Server Icon PNG Conversion...");

            URLConnection iconconnection = new URL(Objects.requireNonNull(guild.getIconUrl())).openConnection();
            //Discord throws a 500 Error when User-Agents are not supplied
            iconconnection.setRequestProperty("User-Agent", "Discord-Themer");
            iconconnection.connect();
            BufferedImage ibufferedImage = ImageIO.read(iconconnection.getInputStream());
            ImageIO.write(ibufferedImage, "png", new File(themeDir.getPath() + "\\" + name + ".png"));
            logger.debug("Server Icon PNG Conversion Done!");

            //Guild Icon
            writer.println("MetaData:icon:" + name);

            logger.debug("Starting Avatar PNG Conversion...");

            URLConnection avatarconnection = new URL(Objects.requireNonNull(jda.getSelfUser().getAvatarUrl())).openConnection();
            //Discord throws a 500 Error when User-Agents are not supplied
            avatarconnection.setRequestProperty("User-Agent", "Discord-Themer");
            avatarconnection.connect();
            BufferedImage abufferedImage = ImageIO.read(avatarconnection.getInputStream());
            ImageIO.write(abufferedImage, "png", new File(themeDir.getPath() + "\\avatar" + name + ".png"));
            logger.debug("Avatar PNG Conversion Done!");

            //Bot Avatar
            writer.println("MetaData:avatar:avatar" + name);

            //Bot Nickname
            writer.println("MetaData:nickname:" + Objects.requireNonNull(guild.getMemberById(jda.getSelfUser().getId())).getNickname());

            //Parser Version
            writer.println("MetaData:parser:" + ParserVersion.currentVersion.getVersionString());

            //Break from MetaData
            writer.println();
            writer.println("//Server Roles");

            //Role Loop
            for (Role role : guild.getRoles()) {
                //Ignore the everyone role & managed roles
                if (role.isPublicRole() || role.isManaged())
                    continue;
                writer.println(role.getId() + ":" + role.getName());
            }

            //We are done here, let's wrap up!
            writer.close();
            logger.info("Current state of server has been exported into a theme file!");

            //Deal with parser!
            if (parse) {
                if (validateTheme(file)) {
                    parseTheme(file);
                    logger.info("Theme File Parsed: " + name + ".dat");
                } else {
                    logger.error("Somehow, we made a perfect theme file and we don't understand it! Please report this on GitHub!");
                }
            }

        } catch (IOException e) {
            logger.error("Error while taking a server snapshot:");
            e.printStackTrace();
            logger.error("Please report this on GitHub!");
        }
    }

    /**
     * Checks if a theme is in the themeMap
     * @param themeName The theme name in question.
     * @return Returns true if the theme is in the themeMap.
     */
    public boolean isValidTheme(String themeName) {
        return themeMap.containsKey(themeName);
    }

    /**

     * @return List of all registered themes.
     */
    public List<String> getThemeList() {
        return Arrays.asList((String[]) themeMap.keySet().toArray());
    }

    /**
     * Gives {@link ThemeToken} associated with the provided theme name.
     * @param themeName The theme name belonging to the token.
     * @return Returns the theme token.
     * @throws ThemeNotFoundException Throws {@link ThemeNotFoundException} when theme is invalid. Use {@link DiscordThemer#isValidTheme(String)} to avoid this.
     */
    public ThemeToken getThemeToken(String themeName) throws ThemeNotFoundException {
        if (isValidTheme(themeName))
            return themeMap.get(themeName);
        throw new ThemeNotFoundException("Theme name is not registered!");
    }

    /**
     * Sets the theme for your guild.
     * @param themeName The theme name to use.
     * @throws ThemeNotFoundException Throws {@link ThemeNotFoundException} when theme is invalid. Use {@link DiscordThemer#isValidTheme(String)} to avoid this.
     */
    public void setServerTheme(String themeName) throws ThemeNotFoundException {
        if (!themeMap.containsKey(themeName)) {
            throw new ThemeNotFoundException("Invalid or Un-parsed Theme-File: " + themeName + "!");
        }

        ThemeToken token = themeMap.get(themeName);

        logger.info("Switching to Theme: " + token.getThemeDisplayName());

        try {
            if (guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER) && token.getServerIconName() != null)
                new RunRestAction(guild.getManager().setIcon(Icon.from(new File(themeDir.getPath() + token.getServerIconName() + ".png"))), actionMode);
            if (token.getBotIconName() != null)
                new RunRestAction(jda.getSelfUser().getManager().setAvatar(Icon.from(new File(themeDir.getPath() + token.getBotIconName() + ".png"))), actionMode);
        } catch (IOException e) {
            logger.error("Your server icon or avatar file(s) are invalid or have been deleted/modified since the last parsing.");
            e.printStackTrace();
        } finally {
            if (guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER) && token.getServerTitle() != null)
                new RunRestAction(guild.getManager().setName(token.getServerTitle()), actionMode);
            if (guild.getSelfMember().hasPermission(Permission.NICKNAME_CHANGE) && token.getBotNickname() != null)
                new RunRestAction(guild.modifyNickname(Objects.requireNonNull(guild.getMemberById(jda.getSelfUser().getId())), token.getBotNickname()), actionMode);
            for (HashMap.Entry<String, String> entry : token.getThemeRoleData().entrySet()) {
                Role crole = guild.getRoleById(entry.getKey());
                if (guild.getSelfMember().canInteract(Objects.requireNonNull(crole)) && guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
                    new RunRestAction(crole.getManager().setName(entry.getValue()), actionMode);
                } else {
                    logger.warn("Cannot Interact with Role ID: " + crole.getId() + ", Skipping!");
                }
            }
            logger.info("The server theme has been updated!");
        }

    }

}