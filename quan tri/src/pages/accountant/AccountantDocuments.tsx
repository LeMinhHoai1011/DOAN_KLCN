import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Filter, Trash2, Eye } from 'lucide-react';
import documentService from '../../services/documentService';
import type { DocumentListItem } from '../../services/documentService';
import StatusBadge from '../../components/StatusBadge';
import clsx from 'clsx';
import { getEffectiveRole } from '../../services/authService';

const AccountantDocuments = () => {
  const navigate = useNavigate();
  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const role = getEffectiveRole();
  const basePath = role === 'ADMIN' ? '/admin' : role === 'EMPLOYEE' ? '/employee' : '/accountant';

  useEffect(() => {
    let isMounted = true;

    const loadDocuments = async () => {
      try {
        const data = await documentService.getDocuments();
        if (isMounted) {
          setDocuments(data);
        }
      } catch {
        if (isMounted) {
          setError('Không thể tải danh sách chứng từ');
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    loadDocuments();

    return () => {
      isMounted = false;
    };
  }, []);

  const filtered = documents.filter((doc) =>
    searchTerm === '' ||
    String(doc.id).includes(searchTerm) ||
    (doc.fileName && doc.fileName.toLowerCase().includes(searchTerm.toLowerCase())) ||
    (doc.supplier && doc.supplier.toLowerCase().includes(searchTerm.toLowerCase()))
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Quản lý chứng từ</h1>
          <p className="text-slate-500 mt-1">Tra cứu và xử lý các chứng từ đã được số hóa</p>
        </div>
        <div className="flex gap-2">
          <button className="flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition-colors">
            <Filter size={18} />
            <span>Bộ lọc</span>
          </button>
          <button
            onClick={() => navigate(`${basePath}/upload`)}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors shadow-sm"
          >
            <span>+ Upload chứng từ</span>
          </button>
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
        <div className="p-4 border-b border-slate-200 flex items-center justify-between bg-slate-50/50">
          <div className="relative max-w-md w-full">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" size={18} />
            <input
              type="text"
              placeholder="Tìm kiếm theo mã, nhà cung cấp..."
              className="w-full bg-white border border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 rounded-lg py-2 pl-9 pr-4 outline-none text-sm transition-all"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
          <div className="text-sm text-slate-500">
            {isLoading ? 'Đang tải...' : `Tổng số ${filtered.length}`}
          </div>
        </div>

        {error ? (
          <div className="p-10 text-center text-red-600">{error}</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50 text-slate-500 text-sm border-b border-slate-200">
                  <th className="py-3 px-4 w-12 text-center"><input type="checkbox" className="rounded border-slate-300 text-blue-600 focus:ring-blue-500" /></th>
                  <th className="py-3 px-4 font-medium">Mã</th>
                  <th className="py-3 px-4 font-medium">Tên chứng từ</th>
                  <th className="py-3 px-4 font-medium">Nhà cung cấp</th>
                  <th className="py-3 px-4 font-medium">Ngày</th>
                  <th className="py-3 px-4 font-medium text-right">Số tiền</th>
                  <th className="py-3 px-4 font-medium">AI Confidence</th>
                  <th className="py-3 px-4 font-medium">Trạng thái</th>
                  <th className="py-3 px-4 font-medium text-center">Thao tác</th>
                </tr>
              </thead>
              <tbody className="text-sm divide-y divide-slate-100">
                {isLoading && (
                  <tr>
                    <td colSpan={9} className="py-10 px-4 text-center text-slate-500">Đang tải danh sách chứng từ...</td>
                  </tr>
                )}
                {!isLoading && filtered.length === 0 && (
                  <tr>
                    <td colSpan={9} className="py-10 px-4 text-center text-slate-500">Chưa có chứng từ nào</td>
                  </tr>
                )}
                {!isLoading && filtered.map((doc) => (
                  <tr key={doc.id} className="hover:bg-slate-50 transition-colors group">
                    <td className="py-3 px-4 text-center"><input type="checkbox" className="rounded border-slate-300 text-blue-600 focus:ring-blue-500" /></td>
                    <td className="py-3 px-4 font-medium text-blue-600 cursor-pointer hover:underline" onClick={() => navigate(`${basePath}/documents/${doc.id}`)}>{doc.id}</td>
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-2">
                        <span className="truncate max-w-[150px]">{doc.fileName}</span>
                      </div>
                    </td>
                    <td className="py-3 px-4 truncate max-w-[200px]" title={doc.supplier || '-'}>{doc.supplier || '-'}</td>
                    <td className="py-3 px-4">{doc.date}</td>
                    <td className="py-3 px-4 text-right font-medium">{doc.amount === undefined ? '-' : `${doc.amount.toLocaleString('vi-VN')} đ`}</td>
                    <td className="py-3 px-4">
                      {doc.aiConfidence === undefined ? '-' : (
                        <span className={clsx(
                          "font-medium",
                          doc.aiConfidence >= 90 ? "text-emerald-600" : doc.aiConfidence >= 80 ? "text-amber-600" : "text-red-600"
                        )}>
                          {doc.aiConfidence}%
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4">
                      <StatusBadge status={doc.status} />
                    </td>
                    <td className="py-3 px-4">
                      <div className="flex items-center justify-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button onClick={() => navigate(`${basePath}/documents/${doc.id}`)} className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded" title="Xem chi tiết">
                          <Eye size={18} />
                        </button>
                        <button className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded" title="Xóa">
                          <Trash2 size={18} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="p-4 border-t border-slate-200 flex justify-between items-center bg-slate-50/50 text-sm">
          <button className="px-4 py-2 border border-slate-200 rounded-lg bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-50">Trước</button>
          <div className="flex gap-1">
            <button className="w-8 h-8 flex items-center justify-center rounded-lg bg-blue-600 text-white font-medium">1</button>
          </div>
          <button className="px-4 py-2 border border-slate-200 rounded-lg bg-white text-slate-600 hover:bg-slate-50">Tiếp</button>
        </div>
      </div>
    </div>
  );
};

export default AccountantDocuments;
