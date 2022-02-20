# Reddit Graph Stream Pipeline

The Reddit Graph Stream Pipeline shows a stream based approach to analyze, filter and create graphs between subreddits. As a data source it uses the Pushshift Reddit Dataset.

todo: bit more explanation

To build the subreddit graph there are four steps required. We are limiting the amount of subreddits we consider to build the graph from in order bring the graph down to a managable size. Weto Strictly speaking
1. Determine the top n subreddits
2. build filter list
3. build subset (use pipe or dataset)
4. build graph (RedditGraphTool)




## Statistics mode

Memory requirements

### Results
Precomputed results:

CSV files:

(Put to an external page.)
Filter lists:
- 10k
- 1k
- 100
- 10
- 5

## Pass through mode
Filter dataset.


## Prerequisites

### Dataset folder structure
The project requires a copy of the Pushshift Reddit Dataset with the same folder structure and filenames as on [files.pushshift.io/reddit/](https://files.pushshift.io/reddit/).

```bash
redditdataset/submissions/RS_2021-06.zst
redditdataset/comments/RC_2021-06.zst
```

### System requirements
>TODO: Specify JAVA version, adjust RAM for 2020 dataset

- Java 11+
- 512 GB RAM: The statistics mode building for the full dataset use up to 340 GB of allocated heap. The graph building up to XXX GB. For subsets of the dataset you can get away with less. The pass through mode is not limited by memory.
- Many CPU cores - strictly speaking not a requirement, but processing on a notebook 

> Processing time on amd Epyc XXX 

## Step by step example

1. find top n subreddits
2. build filter list
3. build subset
4. build graph (RedditGraphTool)

(use pipe or dataset)

# Build and run

Uses the Scala Build Tool (SBT) to build the project.
Use `sbt run` to run the project and `sbt test` to execute run the tests.