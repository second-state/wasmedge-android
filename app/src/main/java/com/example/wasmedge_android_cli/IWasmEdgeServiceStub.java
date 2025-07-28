package com.example.wasmedge_android_cli;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Stub implementation for IWasmEdgeService
 */
public abstract class IWasmEdgeServiceStub extends Binder implements IWasmEdgeService {
    
    private static final String DESCRIPTOR = "com.example.wasmedge_android_cli.IWasmEdgeService";
    
    static final int TRANSACTION_startApiServer = IBinder.FIRST_CALL_TRANSACTION + 0;
    static final int TRANSACTION_startApiServerWithParams = IBinder.FIRST_CALL_TRANSACTION + 1;
    static final int TRANSACTION_stopApiServer = IBinder.FIRST_CALL_TRANSACTION + 2;
    static final int TRANSACTION_isApiServerRunning = IBinder.FIRST_CALL_TRANSACTION + 3;
    static final int TRANSACTION_getApiServerStatus = IBinder.FIRST_CALL_TRANSACTION + 4;
    static final int TRANSACTION_getServerPort = IBinder.FIRST_CALL_TRANSACTION + 5;
    
    public IWasmEdgeServiceStub() {
        this.attachInterface(this, DESCRIPTOR);
    }
    
    public static IWasmEdgeService asInterface(IBinder obj) {
        if (obj == null) {
            return null;
        }
        android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
        if (iin != null && iin instanceof IWasmEdgeService) {
            return (IWasmEdgeService) iin;
        }
        return new Proxy(obj);
    }
    
    @Override
    public IBinder asBinder() {
        return this;
    }
    
    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        String descriptor = DESCRIPTOR;
        switch (code) {
            case INTERFACE_TRANSACTION: {
                reply.writeString(descriptor);
                return true;
            }
            case TRANSACTION_startApiServer: {
                data.enforceInterface(descriptor);
                boolean result = this.startApiServer();
                reply.writeNoException();
                reply.writeInt((result ? 1 : 0));
                return true;
            }
            case TRANSACTION_startApiServerWithParams: {
                data.enforceInterface(descriptor);
                String modelFile = data.readString();
                String templateType = data.readString();
                int contextSize = data.readInt();
                int port = data.readInt();
                boolean result = this.startApiServerWithParams(modelFile, templateType, contextSize, port);
                reply.writeNoException();
                reply.writeInt((result ? 1 : 0));
                return true;
            }
            case TRANSACTION_stopApiServer: {
                data.enforceInterface(descriptor);
                boolean result = this.stopApiServer();
                reply.writeNoException();
                reply.writeInt((result ? 1 : 0));
                return true;
            }
            case TRANSACTION_isApiServerRunning: {
                data.enforceInterface(descriptor);
                boolean result = this.isApiServerRunning();
                reply.writeNoException();
                reply.writeInt((result ? 1 : 0));
                return true;
            }
            case TRANSACTION_getApiServerStatus: {
                data.enforceInterface(descriptor);
                String result = this.getApiServerStatus();
                reply.writeNoException();
                reply.writeString(result);
                return true;
            }
            case TRANSACTION_getServerPort: {
                data.enforceInterface(descriptor);
                int result = this.getServerPort();
                reply.writeNoException();
                reply.writeInt(result);
                return true;
            }
        }
        return super.onTransact(code, data, reply, flags);
    }
    
    private static class Proxy implements IWasmEdgeService {
        private IBinder mRemote;
        
        Proxy(IBinder remote) {
            mRemote = remote;
        }
        
        @Override
        public IBinder asBinder() {
            return mRemote;
        }
        
        public String getInterfaceDescriptor() {
            return DESCRIPTOR;
        }
        
        @Override
        public boolean startApiServer() throws RemoteException {
            Parcel _data = Parcel.obtain();
            Parcel _reply = Parcel.obtain();
            boolean _result;
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                mRemote.transact(TRANSACTION_startApiServer, _data, _reply, 0);
                _reply.readException();
                _result = (0 != _reply.readInt());
            } finally {
                _reply.recycle();
                _data.recycle();
            }
            return _result;
        }
        
        @Override
        public boolean startApiServerWithParams(String modelFile, String templateType, int contextSize, int port) throws RemoteException {
            Parcel _data = Parcel.obtain();
            Parcel _reply = Parcel.obtain();
            boolean _result;
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                _data.writeString(modelFile);
                _data.writeString(templateType);
                _data.writeInt(contextSize);
                _data.writeInt(port);
                mRemote.transact(TRANSACTION_startApiServerWithParams, _data, _reply, 0);
                _reply.readException();
                _result = (0 != _reply.readInt());
            } finally {
                _reply.recycle();
                _data.recycle();
            }
            return _result;
        }
        
        @Override
        public boolean stopApiServer() throws RemoteException {
            Parcel _data = Parcel.obtain();
            Parcel _reply = Parcel.obtain();
            boolean _result;
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                mRemote.transact(TRANSACTION_stopApiServer, _data, _reply, 0);
                _reply.readException();
                _result = (0 != _reply.readInt());
            } finally {
                _reply.recycle();
                _data.recycle();
            }
            return _result;
        }
        
        @Override
        public boolean isApiServerRunning() throws RemoteException {
            Parcel _data = Parcel.obtain();
            Parcel _reply = Parcel.obtain();
            boolean _result;
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                mRemote.transact(TRANSACTION_isApiServerRunning, _data, _reply, 0);
                _reply.readException();
                _result = (0 != _reply.readInt());
            } finally {
                _reply.recycle();
                _data.recycle();
            }
            return _result;
        }
        
        @Override
        public String getApiServerStatus() throws RemoteException {
            Parcel _data = Parcel.obtain();
            Parcel _reply = Parcel.obtain();
            String _result;
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                mRemote.transact(TRANSACTION_getApiServerStatus, _data, _reply, 0);
                _reply.readException();
                _result = _reply.readString();
            } finally {
                _reply.recycle();
                _data.recycle();
            }
            return _result;
        }
        
        @Override
        public int getServerPort() throws RemoteException {
            Parcel _data = Parcel.obtain();
            Parcel _reply = Parcel.obtain();
            int _result;
            try {
                _data.writeInterfaceToken(DESCRIPTOR);
                mRemote.transact(TRANSACTION_getServerPort, _data, _reply, 0);
                _reply.readException();
                _result = _reply.readInt();
            } finally {
                _reply.recycle();
                _data.recycle();
            }
            return _result;
        }
    }
}
