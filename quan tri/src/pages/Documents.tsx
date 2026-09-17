import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Filter, Trash2, Eye } from 'lucide-react';
import StatusBadge from '../components/StatusBadge';
import clsx from 'clsx';

interface Document {
  id: number;
  fileName: string;
  fileType: string;
  fileUrl: string;
  documentType: string;
  status: string;
  uploadDate: string;
}

const Documents = () => {
  const navigate = useNavigate();

  const [searchTerm, setSearchTerm] = useState('');
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('http://localhost:8080/api/documents')
      .then((response) => {
        if (!response.ok) {
          throw new Error('Không thể lấy danh sách chứng từ');
        }

        return response.json();
      })
      .then((data) => {
        setDocuments(data);
        setLoading(false);
      })
      .catch((error) => {
        console.error(error);
        setLoading(false);
      });
  }, []);

  const filteredDocuments = documents.filter((doc) =>
    doc.fileName.toLowerCase().includes(searchTerm.toLowerCase()) ||
    doc.id.toString().includes(searchTerm)
  );

  return (
    <div className="space-y-6">

      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">
            Quản lý chứng từ
          </h1>

          <p className="text-slate-500 mt-1">
            Tra cứu và xử lý các chứng từ đã được số hóa
          </p>
        </div>

        <div className="flex gap-2">

          <button className="flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition-colors">
            <Filter size={18} />
            <span>Bộ lọc</span>
          </button>

          <button
            onClick={() => navigate('/upload')}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors shadow-sm"
          >
            <span>+ Upload chứng từ</span>
          </button>

        </div>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">

        <div className="p-4 border-b border-slate-200 flex items-center justify-between bg-slate-50/50">

          <div className="relative max-w-md w-full">

            <Search
              className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
              size={18}
            />

            <input
              type="text"
              placeholder="Tìm kiếm theo mã, tên chứng từ..."
              className="w-full bg-white border border-slate-200 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 rounded-lg py-2 pl-9 pr-4 outline-none text-sm transition-all"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />

          </div>

          <div className="text-sm text-slate-500">
            {loading
              ? 'Đang tải...'
              : `Tổng số ${filteredDocuments.length} chứng từ`}
          </div>

        </div>

        <div className="overflow-x-auto">

          <table className="w-full text-left border-collapse">

            <thead>
              <tr className="bg-slate-50 text-slate-500 text-sm border-b border-slate-200">

                <th className="py-3 px-4 w-12 text-center">
                  <input
                    type="checkbox"
                    className="rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                  />
                </th>

                <th className="py-3 px-4 font-medium">Mã</th>

                <th className="py-3 px-4 font-medium">
                  Tên chứng từ
                </th>

                <th className="py-3 px-4 font-medium">
                  Loại chứng từ
                </th>

                <th className="py-3 px-4 font-medium">
                  Ngày upload
                </th>

                <th className="py-3 px-4 font-medium text-right">
                  Số tiền
                </th>

                <th className="py-3 px-4 font-medium">
                  AI Confidence
                </th>

                <th className="py-3 px-4 font-medium">
                  Trạng thái
                </th>

                <th className="py-3 px-4 font-medium text-center">
                  Thao tác
                </th>

              </tr>
            </thead>

            <tbody className="text-sm divide-y divide-slate-100">

              {loading ? (

                <tr>
                  <td
                    colSpan={9}
                    className="py-10 text-center text-slate-500"
                  >
                    Đang tải danh sách chứng từ...
                  </td>
                </tr>

              ) : filteredDocuments.length === 0 ? (

                <tr>
                  <td
                    colSpan={9}
                    className="py-10 text-center text-slate-500"
                  >
                    Chưa có chứng từ nào
                  </td>
                </tr>

              ) : (

                filteredDocuments.map((doc) => (

                  <tr
                    key={doc.id}
                    className="hover:bg-slate-50 transition-colors group"
                  >

                    <td className="py-3 px-4 text-center">
                      <input
                        type="checkbox"
                        className="rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                      />
                    </td>

                    <td
                      className="py-3 px-4 font-medium text-blue-600 cursor-pointer hover:underline"
                      onClick={() =>
                        navigate(`/documents/${doc.id}`)
                      }
                    >
                      #{doc.id}
                    </td>

                    <td className="py-3 px-4">

                      <div className="flex items-center gap-2">

                        <span className="truncate max-w-[150px]">
                          {doc.fileName}
                        </span>

                      </div>

                    </td>

                    <td className="py-3 px-4">
                      {doc.documentType || '-'}
                    </td>

                    <td className="py-3 px-4">
                      {doc.uploadDate
                        ? new Date(doc.uploadDate).toLocaleDateString('vi-VN')
                        : '-'}
                    </td>

                    <td className="py-3 px-4 text-right font-medium">
                      -
                    </td>

                    <td className="py-3 px-4">
                      <span className={clsx(
                        "font-medium text-slate-400"
                      )}>
                        -
                      </span>
                    </td>

                    <td className="py-3 px-4">

                      <StatusBadge
                        status={doc.status || 'PROCESSING'}
                      />

                    </td>

                    <td className="py-3 px-4">

                      <div className="flex items-center justify-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">

                        <button
                          onClick={() =>
                            navigate(`/documents/${doc.id}`)
                          }
                          className="p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded"
                          title="Xem chi tiết"
                        >
                          <Eye size={18} />
                        </button>

                        <button
                          className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded"
                          title="Xóa"
                        >
                          <Trash2 size={18} />
                        </button>

                      </div>

                    </td>

                  </tr>

                ))

              )}

            </tbody>

          </table>

        </div>

        <div className="p-4 border-t border-slate-200 flex justify-between items-center bg-slate-50/50 text-sm">

          <button className="px-4 py-2 border border-slate-200 rounded-lg bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-50">
            Trước
          </button>

          <div className="flex gap-1">

            <button className="w-8 h-8 flex items-center justify-center rounded-lg bg-blue-600 text-white font-medium">
              1
            </button>

          </div>

          <button className="px-4 py-2 border border-slate-200 rounded-lg bg-white text-slate-600 hover:bg-slate-50">
            Tiếp
          </button>

        </div>

      </div>

    </div>
  );
};

export default Documents;
