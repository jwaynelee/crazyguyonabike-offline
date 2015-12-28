A tool to write a [crazyguyonabike](http://www.crazyguyonabike.com) journal whilst offline.



# Why? #

On a recent bike trip I kept a crazyguyonabike [journal](http://www.crazyguyonabike.com/doc/benvoyage). I had time in the evening to write my journal but I was usually not connected to the internet at the time. When I did find an internet connection (every few days to weeks) I'd have a big backlog of journal entires and photos to copy/paste from a text editor and upload. This took longer than I expected and soon became annoying. This application allows you to write your journal, add photos and caption them while your offline. When you do get online a single click will upload these new pages automatically.

# Who might use it? #

If you are...

  * On a long tour, keeping a daily journal on crazyguyonabike
  * Carrying a laptop (no use if you use internet cafes)
  * Don't have frequent access to the internet
  * Internet connection (when found) is slow and unreliable

# Limitations #

It doesn't do everything you can with the website. It complements rather than replaces the website interface (specifically making offline updates possible). For everything else you need to use the website. Some notable limititations:

  * Only create new pages
    * Can't edit or view existing pages (doesn't download your entire journal)
  * Pages are added to the end of the journal, no way to insert between pages

The application is in a "working" state, but there is a few missing features I'd like to add:

  * No spell checker
  * Photos are added to the end of a page (you can't use ### tags to insert between paragraphs of text ... just yet)
  * Can't add maps to pages
  * Can't tag pages with locales

The crazyguyonabike website changes frequently as new features are added. As such it is something of a moving target for an application like this to interact with as there is no dedicated interface for applications (as opposed to humans via webbrowser) to use. This application currently creates the HTTP requests that a traditional browser would - [details here](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/documentation/com/cgoab/offline/client/web/DefaultWebUploadClient.html). In face of this challenge a check is performed before uploading to detect if the server has changed and aborts the upload if the changes are thought to be fatal to this application. New updates will be released as soon as possible when changes to the server occur.

# Installation #

  1. Download and install [Java](http://www.java.com) (version `1.6` or above)
    * You can check if you have already have java by typing `java -version` at a cmd prompt and checking the result.
    * Alternatively check using [this webpage](http://www.java.com/en/download/installed.jsp).
  1. (Optionally) install [ImageMagick](http://www.imagemagick.org) (version `6.3.8.3` or above)
    * _If you don't install this your photos will not be resized before uploading_
  1. Download the latest `crazyguyonabike-offline` zip file for your platform from [downloads](http://code.google.com/p/crazyguyonabike-offline/downloads)
  1. Unpack the zip file (for example into `C:\crazyguyonabike-offline\`)
  1. Double click `cgoab-offline.exe` (or run shell script with same name on linux)

# Screenshots (User guide) #

**Note:** Recently added features are listed on the NewAndNoteworthy page.

## Create a new journal ##

Use `File > New Journal` or `New Journal` in the context menu (right click).

Note, the name of the journal does not have to correspond to the journal hosted on crazyguyonabike (although it would make sense to keep them the same!).

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/NewJournal.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/NewJournal.gif)

### Add a page to the journal ###

Again, either use `File > New Page` or the context menu.

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/AddText.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/AddText.gif)

## Drag some photos onto the page ##

Drag photos onto the grey panel beneath the photos text. Photos can be dragged from `Explorer` or from your favourite program to review and tag photos (for example `Adobe Bridge`).

The first time you do this you'll be prompted to use the embedded (EXIF) thumbnail (low quality but fast) or create a new one from scratch (slow but better quality).

Photos can also be added via `Add Photos` from the context menu shown when a page is selected. This shows a traditional file selection dialog.

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/DragDropPhotos.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/DragDropPhotos.gif)

### Decide if you want photos to be resized ###

Photos are resized in the background. At least one dimension will be less than or equal to 1000 pixels. The result is compressed using JPEG quality 70. The CGOAB server will itself resize photos that exceed these dimensions (http://www.crazyguyonabike.com/website/help/#pics_scaling details) so this step simply optimises the photos for upload and avoids wasting upload bandwith.

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/ResizePhotosDialog.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/ResizePhotosDialog.gif)

## Carry on writing journal whilst photos resize in background ##

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/AddedPhotos.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/AddedPhotos.gif)

## Create a 2nd page etc... ##

One done, add a new page, add some photos and repeat.

New pages inherit the settings of the previous day. The date is automatically incremented by one day and if the title is of the form "Day N" then the new title is set to the previous day plus one.

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/2ndPage.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/2ndPage.gif)

## Login and select which of your Journal(s) you want these new pages to be added to ##

When you finally get online, select `Upload` from the context menu.

Decide if you want these new pages to be visible or not. If not you'll need to manually update the pages using the web interface.

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/Visible.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/Visible.gif)

Login with your crazyguyonabike username & password (once logged in a cookie is saved so you shouldn't need to login again).

Once successfully logged in you'll see your list of journals. Select the one you want to add these new pages to and click `Upload`.

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/SelectJournal.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/SelectJournal.gif)

## Wait whilst the pages & (resized) photos are uploaded to the server ##

Wait whilst the upload proceeds. Failures are automatically retried (for example, if the connection drop during the upload it will retry).

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/Upload.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/Upload.gif)

## Errors ##

Errors can occur when you add a page that is older than the last page in the journal, when you add a photo that already exists in your journal or when something unexpected happens on the server. When these errors occur the upload terminates but as there is no easy way to undo the pages and photos that have already been uploaded (as no "transaction" support) the journal is left in an _inconsistent_ state. If the error occurred during a page upload then the page is marked as error. If it occurred during a photo upload, the photo is marked as error and the page it belongs to is marked _partially uploaded_ (the page was created but not all its photos are added). In either case you can edit the non-uploaded pages and photos and retry the upload.

Errors that occur on the server are usually presented with the html sent back from the sever. For example, if the last page in your journal is newer than the page you are uploading then you will get this error:

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/ErrorDateEarlierThanLast.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/ErrorDateEarlierThanLast.gif)

The errors will be hilighted in the UI. Before you re-upload you'll need to fix the date:

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/ErrorUpload.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/ErrorUpload.gif)

Or if you attempt to add a photo that already exists in your journal you'll get this:

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/ErrorFileAlreadyExists.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/ErrorFileAlreadyExists.gif)

Again, the UI will show the error (in this case remove the duplicate photo):

![http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/PartialUpload.gif](http://crazyguyonabike-offline.googlecode.com/svn-history/trunk/crazyguyonabike-offline/screenshots/0.1.0/PartialUpload.gif)

# If something goes wrong? #

View the log file using `About > about` (logs are saved in `~/.cgoaboffline/`, a new log file is created each time application is launched).

Please email or raise an issue in the [issue tracker](http://code.google.com/p/crazyguyonabike-offline/issues/list).