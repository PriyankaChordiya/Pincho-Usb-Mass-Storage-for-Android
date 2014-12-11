package com.felhr.usbmassstorageforandroid.bulkonly;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import java.util.concurrent.atomic.AtomicBoolean;

import commandwrappers.CommandBlockWrapper;
import commandwrappers.CommandStatusWrapper;

/**
 * Created by Felipe Herranz(felhr85@gmail.com) on 9/12/14.
 */
public class BulkOnlyCommunicator
{
    private BulkOnlyStatusInterface statusCallback;

    private UsbFacade usbFacade;

    private AtomicBoolean flagDataIN;
    private AtomicBoolean flagDataOUT;
    private byte[] dataBufferIn;
    private byte[] dataBufferOut;
    private int bufferINSize;

    public BulkOnlyCommunicator(UsbDevice mDevice, UsbDeviceConnection mConnection)
    {
        this.usbFacade = new UsbFacade(mDevice, mConnection);
        this.flagDataIN = new AtomicBoolean(false);
        this.flagDataOUT = new AtomicBoolean(false);
    }

    public boolean startBulkOnly(BulkOnlyStatusInterface statusCallback)
    {
        this.statusCallback = statusCallback;
        usbFacade.setCallback(mCallback);
        return usbFacade.openDevice();
    }

    public void sendCbw(CommandBlockWrapper cbw, byte[] data)
    {
        int dataLength = cbw.getdCBWDataLength();
        if(data != null && dataLength > 0)
        {
            flagDataOUT.set(true);
            dataBufferOut = data;

        }else if(data == null && dataLength > 0)
        {
            flagDataIN.set(true);
            bufferINSize = dataLength;
        }else
        {
            flagDataOUT.set(false);
            flagDataIN.set(false);
        }
        usbFacade.sendCommand(cbw.getCWBuffer());
    }

    public boolean reset()
    {
        return usbFacade.reset();
    }

    public int getMaxLun()
    {
        return usbFacade.getMaxLun();
    }

    private UsbFacadeInterface mCallback = new UsbFacadeInterface()
    {
        @Override
        public void cbwResponse(int response)
        {
            if(response > 0 && flagDataOUT.get()) // CBW correctly sent. Send data.
            {
                statusCallback.onOperationStarted(true);
                usbFacade.sendData(dataBufferOut);
            }else if(response > 0 && flagDataIN.get()) // CBW correctly sent. Receive data.
            {
                statusCallback.onOperationStarted(true);
                usbFacade.requestData(bufferINSize);
            }else if(response > 0) // CBW correctly sent. No data expected.
            {
                statusCallback.onOperationStarted(true);
                usbFacade.requestCsw();
            }else if(response <= 0) // CBW not correctly sent.
            {
                statusCallback.onOperationStarted(false);
            }
        }

        @Override
        public void cswData(byte[] data)
        {
            CommandStatusWrapper csw = CommandStatusWrapper.getCWStatus(data);
            statusCallback.onOperationCompleted(csw);
        }

        @Override
        public void dataFromHost(int response)
        {
            if(response > 0)
                usbFacade.requestCsw();
        }

        @Override
        public void dataToHost(byte[] data)
        {
            if(data != null)
            {
                dataBufferIn = data;
                usbFacade.requestCsw();
            }
        }
    };

}