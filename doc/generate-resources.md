## generating mozc.data
```
git clone https://github.com/google/mozc
cd mozc
git checkout 3.33.6133
bazel build --config=oss_linux //data_manager/oss:mozc_dataset_for_oss

# Generated src/bazel-bin/data_manager/oss/mozc.data
```
