# Contributing guidelines

## Working on an issue

Before starting to work on something, please comment in JIRA or ask on the mailing list
to make sure nobody else is working on it.

The DSE driver uses [semantic versioning](http://semver.org/) and our branches use the following scheme:

```
            1.1.1      1.1.2 ...                1.2.1 ...
         -----*----------*------> 1.1.x      -----*------> 1.2.x
        /                                   /
       /                                   /
      /                                   /
-----*-----------------------------------*-------------------------> 1.x
   1.1.0                               1.2.0        ...

Legend:
 > branch
 * tag
```

- new features are developed on "minor" branches such as `1.x`, where minor releases (ending in `.0`) happen.
- bugfixes go to "patch" branches such as `1.1.x` and `1.2.x`, where patch releases (ending in `.1`, `.2`...) happen.
- patch branches are regularly merged to the bottom (`1.2.x` to `1.x`) so that bugfixes are 
  applied to newer versions too.

Consequently, the branch having the highest major + minor version (in the format `x.x.x`) 
will be the branch to target bugfixes to. The branch in the format `x.x` which has the 
highest major will be the branch to target new features to.

Before you send your pull request, make sure that:

- you have a unit test that failed before the fix and succeeds after.
- the fix is mentioned in `changelog/README.md`.
- the commit message include the reference of the JIRA ticket for automatic linking
  (example: `JAVA-503: Fix NPE when a connection fails during pool construction.`).

As long as your pull request is not merged, it's OK to rebase your branch and push with
`--force`.

If you want to contribute but don't have a specific issue in mind, the [lhf](https://datastax-oss.atlassian.net/secure/IssueNavigator.jspa?reset=true&mode=hide&jqlQuery=project%20%3D%20JAVA%20AND%20status%20in%20(Open%2C%20Reopened)%20AND%20labels%20%3D%20lhf)
label in JIRA is a good place to start: it marks "low hanging fruits" that don't require
in-depth knowledge of the codebase.

## Editor configuration

We use IntelliJ IDEA with the default formatting options, with one exception: check
"Enable formatter markers in comments" in Preferences > Editor > Code Style.

Please format your code and optimize imports before submitting your changes.

## Running the tests

We use TestNG. There are 3 test categories:

- "unit": pure Java unit tests.
- "short" and "long": integration tests that launch DSE instances.

The Maven build uses profiles named after the categories to choose which tests to run:

```
mvn test -Pshort
```

The default is "unit". Each profile runs the ones before it ("short" runs unit, etc.)

Integration tests use [CCM](https://github.com/pcmanus/ccm) to bootstrap DSE instances.

DSE executable jars can be either fetched by CCM, for which credentials can be specified in
the `~/.ccm/.dse.ini` file, or DSE instances can be launched by CCM from a local DSE
build.

Two Maven properties control its execution:

- `cassandra.version`: the **DSE** version. This has a default value in the root POM,
  you can override it on the command line (`-Dcassandra.version=...`).
- `ipprefix`: the prefix of the IP addresses that the Cassandra instances will bind to (see
  below). This defaults to `127.0.1.`.
- `cassandra.directory`: The directory containing DSE's source project, in which CCM
  will find the DSE executable jars to launch a DSE instance, if the source code has been compiled.
  This option is only useful if you wish to launch the Java driver tests against a local
  build of DSE.
  


CCM launches multiple DSE instances on localhost by binding to different addresses. The
driver uses up to 10 different instances (127.0.1.1 to 127.0.1.10 with the default prefix).
You'll need to define loopback aliases for this to work, on Mac OS X your can do it with:

```
sudo ifconfig lo0 alias 127.0.1.1 up
sudo ifconfig lo0 alias 127.0.1.2 up
...
```
