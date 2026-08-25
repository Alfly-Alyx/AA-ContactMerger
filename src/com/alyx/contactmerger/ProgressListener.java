package com.alyx.contactmerger;

public interface ProgressListener {

    void update(float done);

    void abort();

}
