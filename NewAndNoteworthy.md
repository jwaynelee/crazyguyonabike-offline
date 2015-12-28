

# 0.1.5: Bug fix due to site changes #

  * Fixes bug where uploads fail with "Error during initialization" message; caused by renaming of "format" field to "auto\_format".

# 0.1.4: Bug fix due to site changes #

  * Fixes bug where font style (bold/italic) and hidden settings are ignored (always bold, italic & visible). Caused by change to site to use different value to indicate "true" (now "1" not "on").

# 0.1.3: Charset encoding bug, OSX Application menu & bug fixes #

  * If you used non-ASCII characters in the text (for example, ä or à) then previous versions failed to re-open the file. You'd get something similar to the error box below. This version explicitly saves all files in "UTF-8". Older files are converted automatically so will work seamlessly with this new version.

![http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.3/failedToLoadXml.png](http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.3/failedToLoadXml.png)

  * On OSX the Quit, About & Preference actions are now accessed from the specific application menu as shown below.

![http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.3/OSXAppMenu.gif](http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.3/OSXAppMenu.gif)

  * Bug fix
    * NPE when attempting open a journal when another journal is already open.

# 0.1.2: Photo captions, upload selection & bug fixes #

  * If you caption your photos in Picasa (or with similar photo management software) then the caption will be automatically extracted when the photo is added.

![http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.2/picasa.gif](http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.2/picasa.gif)

![http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.2/copied-caption.gif](http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.2/copied-caption.gif)

  * You can now select one or more pages to upload rather than the entire journal. Your selection must include the _first non-uploaded_ page and be _contiguous_ (you can't skip a page).

![http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.2/uploadSinglePage.gif](http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.2/uploadSinglePage.gif)

![http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.2/uploadMultiplePages.gif](http://crazyguyonabike-offline.googlecode.com/svn/trunk/crazyguyonabike-offline/screenshots/0.1.2/uploadMultiplePages.gif)

  * Extra file menu items (add photo, upload etc)
  * Many miscellaneous bug fixes
    * Key bindings not created until menu opened (CTRL-Z etc)
    * No prompt to save when exiting via `File > Exit`
    * Photo panel turns white when active and photos can be dropped onto it, grey when inactive.
    * NPE when restoring default font in preferences
    * NPE when loading a photo with partial meta-data
    * "?:?" shown as version when spaces are in the install path
    * ... and many more.