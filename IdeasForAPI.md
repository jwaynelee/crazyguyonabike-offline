This page suggests an API for crazyguyonabike.

The goal is that applications like cgoab-offline can interact with the site without introducing tight coupling to the sites html face.

The methods to upload content behave much like the existing HTML form interface. This should hopefully ease implementation as the underlying methods called by the forms only need be exposed to this new API, with behaviour remaining consistent.



# General choices #

## Style ##

Methods could be invoked in one of the following ways:

  * Distinct URL per method, for example `/api/GetUserInfo`
  * Single "API" URL, passing action as a parameter, `/api/?action=GetUserInfo`

Both would work so the choice comes down to whatever is easiest to implement.

Query style methods map to `HTTP GET` requests (`GetUserInfo and GetDocuments`)
State change methods map to `HTTP PUT` requests (`Login, Logout, CreatePage & AddPhoto`).

## Versions ##

Anticipating future (breaking) changes to the API we might explicitly force clients to select a specific version of the API. For example, embedding the version in the URL (or a query parameter):

`/api/v1/AddPage`

If (when?) new mandatory parameters are introduced to this method we would introduce a new version, but leave v1 available for old clients:

`/api/v2/AddPage`

The logs will show if anyone is still using v1. Over time v1 can be removed.

This introduces extra complexity on server side. Ultimately it will limit server side changes. For example if the db schema is changed, requiring a new parameter then extra complexity is added by supporting the version that doesn't supply this parameter.

For this reason versioning is considered unnecessary. This is unlikely to be a problem as it's not anticipated that changes to the API will be frequent. Theoretically these APIs are so simple that dramatic server side restructuring can be performed and these APIs can still be supported.

## Response formats ##

Data may be returned from the server in a variety of ways:

| Custom string | Very simple (for the server), requires custom parsing on the client. Not obvious how to treat structured data without re-implementing one the other techniques. |
|:--------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| CSV           | Simple for the server. Need to make sure commas in quotes are escaped in the parser. Poor structured data support. Could adopt "key=value" style to associate key directly with value. |
| JSON          | Simple(ish). Useful if every want to use API from javascript.                                                                                                   |
| XML           | Less simple. But there is good parser support in every language. And XPath allows for structural queries. Represents structured data well.                      |

It's tempting to choose one format for simple single return value calls (ie, `GetUserInfo`) and another for complex structured return types (`GetDocuments`). For consistency one format would be preferred for all calls even if it requires a bit more effort to handle the simple results.

Ultimately it comes down to personal preference (at least between CSV, JSON, XML), but XML is (IMHO) the easiest to parse.

Some examples, inspired by the flickr API:

```
<!-- result from GetUserInfo -->
<result error="false">
  <username>johndoe</username>
  <realname>John Doe</realname>
</result>

<!-- result from GetDocuments -->
<result error="false">
  <document>
    <id>12345</id>
    <name>My Journal</name>
    <type>JOURNAL</type>
    <status>Day 10 out of 100</status>
  </document>
  <document>
    <id>12346</id>
    <name>Another Journal</name>
    <type>JOURNAL</type>
    <status>Completed Aug 2010</status>
  </document>
</result>
```

An error result would be,

```
<result error="true">
  <code>-100</code>
  <description>You are not logged in</description>
</result>
```

## Errors ##

Closely associated with the response format is how error conditions are indicated. For consistency with unix/c APIs error codes are expected to be negative values. For example:

| **Error Code** | **Value** | **Description** |
|:---------------|:----------|:----------------|
| DISABLED\_METHOD | -300      | Method is currently disabled. This allows temporary suspension of certain services (for example, if abuse is detected). May be returned from any method. |
| INVALID\_ARGUMENTS | -200      | Arguments list is not valid (ie, missing or extra argument). May be returned from any method. |
| NOT\_LOGGED\_IN | -100      | Need to login (or login failed). May be returned from any method. |

As shown in the XML error response snippet, both the code and a descriptive message are returned. This allows the client to take specific error handling steps for errors it can handle (detect NOT\_LOGGED\_IN and present a login dialogue) and for errors it can't handle then pop up a message box that gives the user a useful message).

## Session handling ##

HTTP requests to the API contain whatever cookies may have been set by the server (connections behave just like a browser).

Alternatively clients might be required to present a session id/token for each method as a query parameter. This simplifies clients as they no longer need to worry about managing the HTTP cookies which in some HTTP toolkits is a cumbersome task.

# Methods #
## GetUserInfo ##

A simple query to find the user-name and real-name of the current user. This can also be used as a check to see if an old session is still valid and thus avoid prompting the user for a password every time:

### parameters ###

_empty_

### returns ###

Structure containing:

| username | "testuser" |
|:---------|:-----------|
| realname | "John Doe" |

or error code,

| NOT\_LOGGED\_IN |
|:----------------|

### example ###

`GET /api/GetUserInfo`

```
<result error="false">
  <username>testuser</username>
  <realname>John Doe</realname>
</result>
```

---

## LogIn ##

Log in and establishe a session on the server. This must be called before the session APIs (GetDocuments, CreatePage etc) are called.

### parameters ###

| username |
|:---------|
| password |

### returns ###

Empty response if success, or error code.

| NOT\_LOGGED\_IN | if credentials invalid, no distinction is made if username or password was invalid |
|:----------------|:-----------------------------------------------------------------------------------|
| ACCOUNT\_LOCKED | if repeated attempts are made to log in with incorrect password                    |

Upon success a side-affect of setting the session cookie, which will be returned to the server for subsequent requests.

### example ###
```
<!-- success -->
<result error="false"/>

<!-- failure -->
<result error="true">
  <error>-100</error>
  <description>Not logged in</description>
</result>
```

---

## LogOut ##

Logs out from the server. It is expected that the server instructs the client to clear any session cookie. May be used by server to clean up server side session object.

### parameters ###

_empty_

### returns ###

Always succeeds. An attempt to logout without first logging in is silently ignored.

Note, this has the side-affect of clearing the session cookie.

### example ###
```
<result error="false"/>
```

---

## GetDocuments ##

Returns list of documents authored by the user.

### parameters ###

_empty_

### returns ###

A list containing an entry for each document owned by the current user, each document has:

| **property** | **description** | **example** |
|:-------------|:----------------|:------------|
| id           | ID of the document | 12345       |
| name         | Name of the document | "My Journal" |
| status       | Status of the document | "Completed Aug 2010" or "10 out of 100 days" |
| type         | Type of document | JOURNAL or ARTICLE |

or error code,

| NOT\_LOGGED\_IN |
|:----------------|
| ...             |

### example ###
`GET /api/GetDocuments`

```
<result error="false">
  <document>
    <id>12345</id>
    <name>My Journal</name>
    <type>JOURNAL</type>
    <status>Day 10 out of 100</status>
  </document>
  <document>
    <id>12346</id>
    <name>Another Journal</name>
    <type>JOURNAL</type>
    <status>Completed Aug 2010</status>
  </document>
</result>
```

---

## CreatePage ##

Creates a new page in a document.

### parameters ###

| **parameter** | **description** |
|:--------------|:----------------|
| doc\_id       | id of document  |
| title         | `string`        |
| headline      | `string`        |
| text          | `string`        |
| date          | `yyyy-mm-dd`, may be empty |
| distance      | numeric value, may be empty |
| indent        | `1..10`         |
| is\_bold      | `true|false`    |
| is\_italic    | `true|false`    |
| style         | `large|medium|small` |
| sequence      | integer describing insertion point of page in the document. Magic value "-1" will append page to end of the document. |

### returns ###

| page\_id |
|:---------|

or error code,

| NOT\_LOGGED\_IN |
|:----------------|
| PAGE\_OLDER\_THAN\_PREVIOUS | if previous page in document is **newer** than page attempting to add |
| INVALID\_PHOTO\_TAG | if the text contains "###" tags that reference an invalid photo |
| ...(expect more errors can occur here)... |

### example ###
`POST /api/CreatePage`

```
<result error="false">
  <pageid>12345</pageid>
</result>
```

---

## AddPhoto ##

Attaches a photo to a page. The "###" tag is appended to the end of the text.

### paramaters ###

| page\_id | id of page to add photo to |
|:---------|:---------------------------|
| comment  | comment, may be empty      |
| file     | file stream                |
| name     | name of the photo, defaults top filename if empty |

### returns ###

On success returns empty result.

Or error,

| NOT\_LOGGED\_IN |
|:----------------|
| PHOTO\_ALREADY\_EXISTS | if a photo with the same name already exists in the journal |
| ...(expect more errors can occur here)... |

### example ###
`POST /api/AddPhoto`

```
<result error="false"/>
```

# Limitations (Extra APIs?) #

## Embedding photos in the text ##

With the above API a combination of `CreatePage` and several calls to `AddPhoto` are required to fully upload a page. A limitation of this approach is that photos can only be added at the _end_ of a page. For example, it is not possible to call `CreatePage` with text that embeds "###" photo tags (as the photos don't yet exist and so we'll get an error!).

One solution is to allow _unbound_ "###" tags. And if (when) the photos  finally exist on the server then the tags are bound.

An alternative approach would be to call `CreatePage` with empty text, call `AddPhoto` for each photo and then call a method `UpdatePage` that would set the actual text (which would contain embedded "###" tags). For example;

```
UpdatePage( page_id, text ) {
  /* sets the text of the selected page */
}
```

It's preferable to avoid introducing extra APIs but the first solution can run into difficulties handling errors. For example, suppose we create a page with text containing ###1 & ###2 (where 1 and 2 are photos we'll later add). If we fail to upload photo#2 (perhaps it another photos with that name already exists) then we get stuck since we can no longer change the text. If `UpdatePage` exists we could just set the text (after removing the references to the photos that failed to upload).

Right now the app doesn't require these APIs are photos cannot be embedded in the text (a limitation of the UI that may change in future).

## Transactions ##

Robust clients require sophisticated error handling as the server offers no transactional features. For example, to upload 2 pages, each containing 3 photos will result in the following sequence of method calls:

```
1. CreatePage
2. AddPhoto
3. AddPhoto
4. AddPhoto
5. CreatePage
6. AddPhoto
7. AddPhoto
8. AddPhoto
```

Clients need to be able aware of and able to handle failures in any of these steps. Note, some operations _can_ be retried. For example, if the connection drops during AddPhoto then it is OK to just wait until the connection reconnects and retry the AddPhoto operation. This is a common case so catches most problems. But if the client crashes, or perhaps one of these operations fail with a _logical_ error (e.g, PHOTO\_ALREADY\_EXISTS) then the journal is left in an inconsistent state as only part of the work is done.

In an ideal world we would have server side transactions and wrap each page upload in a transaction allowing the client to either fully commit (or discard) an upload.

A rough implementation would change the upload methods (CreatePage and AddPhoto) to store data in a temporary directory & table on the server (performing as much error handling as is possible to catch errors early). Once all the data is transferred to the server the client can call `Commit` and these files & data will be inserted into the "real" database. Note, we're not talking about a need for rigorous atomicity on the server. It's OK if some manual intervention is required if the server crashes during transaction commit. But then this sort of event is much less common than a client side failure.

Example client:

```
1. CreateTransaction
2. CreatePage
3. AddPhoto
4. AddPhoto
5. AddPhoto
6. Commit
7. /* repeat for next page and so on */
```

Sketch of API extensions

```
CreateTransaction {
  /* creates a folder on the server for photos to be added to */
  /* creates an entry in the pending-transactions table */
  /* returns a transaction id */
}

CreatePage {
  /* stores data into pending-transaction table */
}

AddPhoto {
  /* stores photo into transaction temp folder */
}

Commit {
  /* copies data from pending transaction table to main table */
  /* copies photos from transaction temp folder to main folder */
  /* clean up */
}

Rollback {
  /* called when client decides to abort upload 
  /* (could be because user cancelled the upload as it's taking to long) */
  /* remove transaction temp folder and pending transaction table */
}
```

Notes,

  * Need periodic "garbage collecter" to remove transactions that were created but never committed (for example, because of client crash). This might be a simple cron job that deletes transaction that have not been touched in 1hr (and also delete transaction temp folder)

It might seem simpler to have the client upload a page and all it's photos in one single operation (ie, `UploadPageAndPhotos`). But the chance of this failing is much higher, especially on a slow connection. The transactional approach allows clients to slowly build up the data on the server in small pieces, retrying individual operations as necessary before finally committing the whole lot.