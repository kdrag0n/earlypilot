# Patreon download server

This is a server that provides downloads for Patreon benefits. It handles authentication, benefit validation, and content serving out-of-the-box. Additionally, filters can be applied to content as it is being served, which allows watermarking downloads to discourage leaks and/or personalizing downloads for each user.

## Filters

The default content filter implementation is a simple passthrough filter that serves static data from storage with no modification. No watermarking filters are available in the public source code because making them public would defeat the purpose of watermarking, so you will need to create your own filter if you want to track the source of leaks. 

The following objects are available to filters:

- File input stream (from static storage)
- Ktor application environment
- Ktor request and call
- Output stream (to return data to the user)

The Apache Commons Compress and Google Guava libraries are also available for use in filters.

To make a custom filter, create a class that implements `dev.kdrag0n.patreondl.filters.ContentFilter` and re-compile the server with your class included. The filter can be activated by setting `web.contentfilter` to the fully-qualified class name in the server configuration file. 