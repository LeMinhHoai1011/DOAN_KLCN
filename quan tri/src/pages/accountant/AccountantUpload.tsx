import { useRef, useState, useEffect } from 'react';
import { CheckCircle2, FileType, Loader2, UploadCloud, XCircle } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import documentService from '../../services/documentService';
import type { DocumentResponse, DocumentType } from '../../services/documentService';
import { getCurrentUser, getEffectiveRole } from '../../services/authService';

type UploadState = 'idle' | 'uploading' | 'completed' | 'error';

const formatFileSize = (size: number) => {
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
};

const AccountantUpload = () => {
  const navigate = useNavigate();
    const fileInputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [uploadState, setUploadState] = useState<UploadState>('idle');
  const [uploadResult, setUploadResult] = useState<DocumentResponse | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([]);
  const [selectedTypeId, setSelectedTypeId] = useState<number | ''>('');

  useEffect(() => {
    documentService.getDocumentTypes()
      .then(setDocumentTypes)
      .catch(() => setErrorMessage('Không thể tải danh sách loại chứng từ'));
  }, []);

  const handleFileSelect = (selectedFile: File | undefined) => {
    if (!selectedFile) return;

    setUploadResult(null);
    setErrorMessage('');

    if (selectedFile.size > 10 * 1024 * 1024) {
      setFile(null);
      setUploadState('idle');
      setErrorMessage('Kích thước file không được vượt quá 10MB');
      return;
    }

    setFile(selectedFile);
    setUploadState('idle');
  };

  const handleUpload = async () => {
    if (!file) return;

    setUploadState('uploading');
    setErrorMessage('');

    try {
      const typeIdToUpload = selectedTypeId !== '' ? selectedTypeId : undefined;
      const response = await documentService.uploadDocument(file, typeIdToUpload);
      setUploadResult(response);
      setUploadState('completed');
    } catch {
      setUploadState('error');
      setErrorMessage('Upload thất bại. Vui lòng thử lại.');
    }
  };

  const handleCancel = () => {
    setFile(null);
    setUploadResult(null);
    setUploadState('idle');
    setErrorMessage('');
  };
  
  // Determine base path based on current location to navigate correctly back
  const getBasePath = () => {
    const role = getEffectiveRole(getCurrentUser());
    if (role === 'ADMIN') return '/admin';
    if (role === 'ACCOUNTANT') return '/accountant';
    return '/employee';
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Upload Chứng Từ</h1>
        <p className="text-slate-500 mt-1">Kéo thả file vào đây để tải lên hệ thống</p>
      </div>

      <div className="bg-white p-8 rounded-2xl border border-slate-200 shadow-sm">
        {!file ? (
          <div
            className="border-2 border-dashed border-blue-300 bg-blue-50/50 hover:bg-blue-50 transition-colors rounded-xl p-12 flex flex-col items-center justify-center text-center cursor-pointer relative"
            onClick={() => fileInputRef.current?.click()}
          >
            <div className="w-16 h-16 bg-white rounded-full flex items-center justify-center shadow-sm mb-4">
              <UploadCloud className="text-blue-500" size={32} />
            </div>
            <h3 className="text-lg font-semibold text-slate-800 mb-1">Kéo thả chứng từ vào đây</h3>
            <p className="text-sm text-slate-500 mb-6">Hỗ trợ các định dạng: PDF, JPG, PNG, XML (tối đa 10MB)</p>
            <button
              type="button"
              onClick={(event) => { event.stopPropagation(); fileInputRef.current?.click(); }}
              className="px-6 py-2.5 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 shadow-sm transition-all"
            >
              Chọn File
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png,.xml"
              className="hidden"
              onChange={(event) => handleFileSelect(event.target.files?.[0])}
            />
          </div>
        ) : (
          <div className="flex flex-col items-center">
            <div className="w-full flex items-center justify-between bg-slate-50 border border-slate-200 p-4 rounded-xl mb-6">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-blue-100 text-blue-600 rounded-lg">
                  <FileType size={24} />
                </div>
                <div className="text-left">
                  <div className="font-medium text-slate-800">{file.name}</div>
                  <div className="text-xs text-slate-500">{formatFileSize(file.size)}</div>
                </div>
              </div>
              {uploadState === 'completed' && <CheckCircle2 className="text-emerald-500" size={24} />}
              {uploadState === 'error' && <XCircle className="text-red-500" size={24} />}
            </div>
            
            <div className="w-full max-w-lg mb-8 text-left space-y-2">
              <label className="block text-sm font-medium text-slate-700">Loại chứng từ</label>
              <select 
                value={selectedTypeId} 
                onChange={(e) => setSelectedTypeId(e.target.value ? Number(e.target.value) : '')}
                disabled={uploadState === 'uploading' || uploadState === 'completed'}
                className="w-full p-2.5 bg-white border border-slate-300 rounded-lg shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">-- Chưa phân loại --</option>
                {documentTypes.map(type => (
                  <option key={type.id} value={type.id}>{type.name}</option>
                ))}
              </select>
            </div>

            <div className="w-full max-w-lg">
              <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
                {uploadState === 'uploading' && <Loader2 className="text-blue-500 animate-spin" size={22} />}
                {uploadState === 'completed' && <CheckCircle2 className="text-emerald-500" size={22} />}
                {uploadState === 'error' && <XCircle className="text-red-500" size={22} />}
                {uploadState === 'idle' && <UploadCloud className="text-slate-400" size={22} />}
                <span className="text-sm font-medium text-slate-700">
                  {uploadState === 'uploading' && 'Đang tải lên...'}
                  {uploadState === 'completed' && 'Upload thành công'}
                  {uploadState === 'error' && 'Upload thất bại'}
                  {uploadState === 'idle' && 'Sẵn sàng tải lên'}
                </span>
              </div>

              {uploadResult && (
                <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
                  <div>Đã tạo chứng từ với mã: <strong>{uploadResult.id}</strong></div>
                  <div>Trạng thái: <strong>{uploadResult.status}</strong></div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {errorMessage && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {errorMessage}
        </div>
      )}

      {file && uploadState !== 'uploading' && (
        <div className="flex justify-end gap-3">
          <button onClick={handleCancel} className="px-5 py-2.5 bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition-colors">
            Hủy
          </button>
          {uploadState === 'completed' ? (
            <button onClick={() => navigate(`${getBasePath()}/documents/${uploadResult?.id}`)} className="px-5 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow-sm transition-colors font-medium">
              Xem chứng từ
            </button>
          ) : (
            <button onClick={handleUpload} className="px-5 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow-sm transition-colors flex items-center gap-2 font-medium">
              <UploadCloud size={18} /> Tải lên
            </button>
          )}
        </div>
      )}
    </div>
  );
};

export default AccountantUpload;

